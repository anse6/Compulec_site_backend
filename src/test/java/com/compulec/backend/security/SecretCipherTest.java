package com.compulec.backend.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecretCipherTest {

    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void encryptsWithRandomIv_AndDecrypts() {
        SecretCipher cipher = new SecretCipher(KEY);
        String a = cipher.encrypt("JBSWY3DPEHPK3PXP");
        String b = cipher.encrypt("JBSWY3DPEHPK3PXP");
        assertNotEquals(a, b);
        assertFalse(a.contains("JBSWY3DPEHPK3PXP"));
        assertEquals("JBSWY3DPEHPK3PXP", cipher.decrypt(a));
    }

    @Test
    void tamperedDataIsRejected() {
        SecretCipher cipher = new SecretCipher(KEY);
        String encrypted = cipher.encrypt("secret");
        char[] chars = encrypted.toCharArray();
        chars[20] = chars[20] == 'A' ? 'B' : 'A';
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(new String(chars)));
    }
}
