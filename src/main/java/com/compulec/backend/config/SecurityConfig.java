package com.compulec.backend.config;

import com.compulec.backend.security.JwtAuthenticationFilter;
import com.compulec.backend.security.RateLimitFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuration principale de la sécurité Spring Security.
 * Configure les routes, JWT, sessions stateless et CORS.
 *
 * Note : Les beans UserDetailsService, PasswordEncoder et AuthenticationProvider
 * sont définis dans ApplicationConfig pour éviter les dépendances circulaires.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;
    private final RateLimitFilter rateLimitFilter;

    /**
     * Routes publiques accessibles sans authentification.
     */
    private static final String[] WHITE_LIST_URLS = {
            "/auth/login",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/auth/invitations/preview",
            "/auth/invitations/accept",
            "/auth/mfa/verify",
            "/auth/mfa/setup",
            "/auth/mfa/setup/confirm",
            "/auth/refresh-token",
            "/auth/logout",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api-docs/**",
            "/actuator/**",
            "/uploads/**",
            // ---- Chat public (visiteurs anonymes) ----
            "/chat/start",
            "/chat/message",
            "/chat/request-admin/**",
            "/chat/history/**",
            "/chat/stream/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Désactiver CSRF (API REST stateless)
                .csrf(AbstractHttpConfigurer::disable)
                // Configurer CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Configurer les autorisations
                .authorizeHttpRequests(auth -> auth
                        // ── Suite asynchrone d'une requête déjà autorisée (flux temps réel SSE) ──
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()

                        // ── OPTIONS pour CORS preflight ──────────────────────────────────
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()

                        // ── Routes publiques sans méthode spécifique (liste blanche) ─────
                        .requestMatchers(WHITE_LIST_URLS).permitAll()

                        // ── Galerie : lecture publique ────────────────────────────────────
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/galleries",
                                "/galleries/projet/**"
                        ).permitAll()

                        // ── Projets / Articles : lecture publique des contenus PUBLIÉS ────
                        // (la liste complète, brouillons compris, exige ADMIN / MANAGER ;
                        //  le détail d'un brouillon renvoie 404 aux visiteurs)
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/projets/publies",
                                "/projets/*",
                                "/articles/publies",
                                "/articles/*"
                        ).permitAll()

                        // ── Consultations : soumission publique (visiteur) ─────────────────
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/consultations"
                        ).permitAll()

                        // ── Contacts : soumission publique (visiteur) ──────────────────────
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/contacts"
                        ).permitAll()

                        // ── Tout le reste nécessite une authentification ───────────────────
                        .anyRequest().authenticated()
                )
                // 401 (et non 403) quand le jeton est absent, expiré ou révoqué :
                // le frontend tente alors automatiquement un refresh.
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                            "{\"success\":false,\"message\":\"Authentification requise ou session expirée\"}");
                }))
                // Session stateless (JWT)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // Provider d'authentification
                .authenticationProvider(authenticationProvider)
                // Ajouter le filtre JWT avant le filtre standard
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // Limitation de débit avant toute autre vérification
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:5174",
                "http://localhost:5175",
                "http://localhost:3000",
                "http://127.0.0.1:5173",
                "http://192.168.1.193:5173",
                "http://192.168.1.193:5174",
                "http://192.168.1.193:5175"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
