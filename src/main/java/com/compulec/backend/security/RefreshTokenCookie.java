package com.compulec.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Cookie du refresh token : HttpOnly (illisible par JavaScript, donc à l'abri d'une faille XSS),
 * SameSite=Strict (jamais envoyé depuis un autre site) et limité aux endpoints /api/auth.
 * En production (HTTPS), mettez app.cookie.secure=true.
 */
@Component
public class RefreshTokenCookie {

    public static final String NAME = "compulec_rt";
    private static final String PATH = "/api/auth";

    @Value("${app.cookie.secure:false}")
    private boolean secure;

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long maxAgeMs;

    public String create(String refreshToken) {
        return ResponseCookie.from(NAME, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(Duration.ofMillis(maxAgeMs))
                .build()
                .toString();
    }

    public String clear() {
        return ResponseCookie.from(NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(0)
                .build()
                .toString();
    }
}
