package com.compulec.backend.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * Codes à usage unique basés sur le temps (TOTP, RFC 6238) :
 * compatibles Google Authenticator, Microsoft Authenticator, Authy, 2FAS…
 *
 * Paramètres standard : HMAC-SHA1, 6 chiffres, période de 30 secondes,
 * tolérance d'une période avant/après pour le décalage d'horloge.
 */
@Component
public class TotpService {

    public static final String ISSUER = "COMPULEC";
    private static final int PERIOD_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int WINDOW = 1;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Nouveau secret de 160 bits encodé en Base32 (format attendu par les applications). */
    public String newSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /** URI otpauth:// à afficher sous forme de QR code. */
    public String otpAuthUri(String secret, String accountEmail) {
        String label = URLEncoder.encode(ISSUER + ":" + accountEmail, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + ISSUER
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    /**
     * Vérifie un code et renvoie la période (step) correspondante, ou -1.
     * Un code dont la période est inférieure ou égale à {@code lastUsedStep} est refusé (anti-rejeu).
     */
    public long verify(String secret, String code, Long lastUsedStep) {
        if (secret == null || code == null || !code.matches("^[0-9]{6}$")) {
            return -1;
        }
        long current = currentStep();
        for (long step = current - WINDOW; step <= current + WINDOW; step++) {
            if (lastUsedStep != null && step <= lastUsedStep) {
                continue;
            }
            if (MessageDigest.isEqual(codeAt(secret, step).getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII))) {
                return step;
            }
        }
        return -1;
    }

    public long currentStep() {
        return Instant.now().getEpochSecond() / PERIOD_SECONDS;
    }

    /** Code attendu pour une période donnée (public pour les tests). */
    public String codeAt(String secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(base32Decode(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return String.format("%0" + DIGITS + "d", binary % (int) Math.pow(10, DIGITS));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 indisponible", e);
        }
    }

    static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return out.toString();
    }

    static byte[] base32Decode(String encoded) {
        String clean = encoded.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Secret Base32 invalide");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buffer >> (bits - 8)) & 0xFF));
                bits -= 8;
            }
        }
        return out.array();
    }
}
