package com.compulec.backend.chat;

import com.compulec.backend.entity.Role;
import com.compulec.backend.entity.User;
import com.compulec.backend.repository.ChatSessionRepository;
import com.compulec.backend.repository.UserRepository;
import com.compulec.backend.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * E-mails d'alerte à l'équipe quand un visiteur demande un conseiller.
 *
 * Envoyés en arrière-plan, après validation en base : la demande du visiteur répond tout de suite
 * (l'envoi SMTP peut prendre plusieurs secondes par destinataire), et l'équipe est déjà prévenue
 * instantanément par le temps réel (son + notification).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatTeamNotifier {

    private final ChatSessionRepository chatSessionRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onChatEvent(ChatEvent event) {
        if (event.type() != ChatEvent.Type.TRANSFER_REQUESTED) {
            return;
        }
        chatSessionRepository.findBySessionToken(event.sessionToken()).ifPresent(session -> {
            List<User> team = new ArrayList<>(userRepository.findByRoleAndActifTrue(Role.ADMIN));
            team.addAll(userRepository.findByRoleAndActifTrue(Role.MANAGER));
            for (User member : team) {
                try {
                    emailService.sendAdminChatNotificationEmail(member, session);
                } catch (Exception e) {
                    log.error("Échec de l'alerte chat pour l'utilisateur {} : {}", member.getId(), e.getMessage());
                }
            }
            log.info("Session {} : {} membre(s) de l'équipe alerté(s) par e-mail", session.getId(), team.size());
        });
    }
}
