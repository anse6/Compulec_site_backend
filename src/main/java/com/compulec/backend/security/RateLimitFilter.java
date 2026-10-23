package com.compulec.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

/**
 * Limitation de débit par adresse IP (réponse 429 + en-tête Retry-After).
 * Protège la connexion, la 2FA, les liens d'invitation / réinitialisation
 * et les formulaires publics (contact, consultation, chat) contre la force brute et le spam.
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String name, String method, String path, int limit, long windowMs) {
        boolean matches(String m, String p) {
            return method.equals(m) && (path.endsWith("/**") ? p.startsWith(path.substring(0, path.length() - 3)) : path.equals(p));
        }
    }

    private static final long MINUTE = 60_000;

    /** Première règle qui correspond ; les chemins sont relatifs au context-path /api. */
    private static final List<Rule> RULES = List.of(
            new Rule("login", "POST", "/auth/login", 10, 5 * MINUTE),
            new Rule("mfa", "POST", "/auth/mfa/**", 10, 5 * MINUTE),
            new Rule("invitation", "POST", "/auth/invitations/**", 10, 5 * MINUTE),
            new Rule("reset", "POST", "/auth/reset-password", 10, 5 * MINUTE),
            new Rule("forgot", "POST", "/auth/forgot-password", 5, 15 * MINUTE),
            new Rule("refresh", "POST", "/auth/refresh-token", 30, MINUTE),
            new Rule("contact", "POST", "/api/contacts", 5, 10 * MINUTE),
            new Rule("consultation", "POST", "/api/consultations", 5, 10 * MINUTE),
            new Rule("chat-start", "POST", "/chat/start", 10, 10 * MINUTE),
            new Rule("chat", "POST", "/chat/**", 30, MINUTE));

    /** Limite générale, large, pour tout le reste. */
    private static final Rule GLOBAL = new Rule("global", "*", "/**", 300, MINUTE);

    private final RateLimiter limiter = new RateLimiter(Clock.systemUTC());

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    /** N'activer que derrière un proxy de confiance (sinon l'en-tête peut être falsifié). */
    @Value("${app.rate-limit.trust-forwarded-for:false}")
    private boolean trustForwardedFor;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!enabled || "OPTIONS".equals(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI().substring(request.getContextPath().length());
        String ip = clientIp(request);
        Rule rule = RULES.stream().filter(r -> r.matches(request.getMethod(), path)).findFirst().orElse(GLOBAL);

        long retryAfter = limiter.tryAcquire(rule.name() + ":" + ip, rule.limit(), rule.windowMs());
        if (retryAfter > 0) {
            log.warn("Limite de débit « {} » atteinte pour {} sur {}", rule.name(), ip, path);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"Trop de requêtes. Réessayez dans "
                    + retryAfter + " seconde(s).\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
