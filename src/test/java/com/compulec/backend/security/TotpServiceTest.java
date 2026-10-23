package com.compulec.backend.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TotpServiceTest {

    private final TotpService totp = new TotpService();

    /** Vecteurs officiels de la RFC 6238 (SHA-1), tronqués à 6 chiffres. */
    @Test
    void matchesRfc6238TestVectors() {
        String secret = TotpService.base32Encode("12345678901234567890".getBytes(StandardCharsets.US_ASCII));
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", secret);
        assertEquals("287082", totp.codeAt(secret, 59 / 30));
        assertEquals("081804", totp.codeAt(secret, 1111111109L / 30));
        assertEquals("005924", totp.codeAt(secret, 1234567890L / 30));
    }

    @Test
    void acceptsCurrentCode_RejectsReplayAndGarbage() {
        String secret = totp.newSecret();
        String code = totp.codeAt(secret, totp.currentStep());
        long step = totp.verify(secret, code, null);
        assertTrue(step > 0);
        assertEquals(-1, totp.verify(secret, code, step), "Un code déjà utilisé doit être refusé");
        assertEquals(-1, totp.verify(secret, "abc", null));
        assertEquals(-1, totp.verify(secret, totp.codeAt(secret, totp.currentStep() - 10), null));
    }

    @Test
    void base32RoundTrip() {
        byte[] data = {1, 2, 3, (byte) 250, 99, 0, 42};
        assertArrayEquals(data, TotpService.base32Decode(TotpService.base32Encode(data)));
    }
}
