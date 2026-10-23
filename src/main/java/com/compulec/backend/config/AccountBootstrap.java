package com.compulec.backend.config;

import com.compulec.backend.entity.Role;
import com.compulec.backend.entity.Sexe;
import com.compulec.backend.entity.User;
import com.compulec.backend.repository.UserRepository;
import com.compulec.backend.security.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Initialisation des comptes au démarrage (remplace l'inscription publique /auth/register).
 *
 * 1. Les comptes créés avant le système d'invitation sont marqués comme activés.
 * 2. Si aucun administrateur n'existe, un premier admin est créé à partir de
 *    app.bootstrap.admin.email / app.bootstrap.admin.password (config/secrets.properties).
 *    Une fois l'admin créé, retirez le mot de passe du fichier.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin.email:}")
    private String adminEmail;

    @Value("${app.bootstrap.admin.password:}")
    private String adminPassword;

    @Value("${app.bootstrap.admin.nom:Administrateur}")
    private String adminNom;

    @Value("${app.bootstrap.admin.prenom:Compulec}")
    private String adminPrenom;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int migrated = userRepository.markLegacyUsersActivated();
        if (migrated > 0) {
            log.info("{} compte(s) existant(s) marqué(s) comme activé(s)", migrated);
        }

        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("Aucun administrateur n'existe. Renseignez app.bootstrap.admin.email et "
                    + "app.bootstrap.admin.password dans config/secrets.properties puis redémarrez.");
            return;
        }

        User admin = User.builder()
                .nom(adminNom)
                .prenom(adminPrenom)
                .email(adminEmail.trim())
                .phone("000000000")
                .dateNaissance(LocalDate.of(1990, 1, 1))
                .fonction("Administrateur")
                .sexe(Sexe.HOMME)
                .role(Role.ADMIN)
                .actif(true)
                .activatedAt(LocalDateTime.now())
                .build();
        PasswordPolicy.validate(adminPassword, adminPassword, admin);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        userRepository.save(admin);

        log.warn("Premier administrateur créé ({}). Complétez son profil dans l'interface, "
                + "puis SUPPRIMEZ app.bootstrap.admin.password de config/secrets.properties.", admin.getEmail());
    }
}
