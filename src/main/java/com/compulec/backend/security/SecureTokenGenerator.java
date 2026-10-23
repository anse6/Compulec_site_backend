package com.compulec.backend.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Génération de secrets cryptographiquement sûrs (SecureRandom, jamais java.util.Random).
 */
@Component
public class SecureTokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Jeton opaque de 256 bits, encodé en Base64 URL-safe (43 caractères). */
    public String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Code numérique à 6 chiffres (000000 – 999999). */
    public String newCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    /** Empreinte SHA-256 hexadécimale, utilisée pour stocker les jetons en base. */
    public String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
