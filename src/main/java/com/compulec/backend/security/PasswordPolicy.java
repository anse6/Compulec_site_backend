package com.compulec.backend.security;

import com.compulec.backend.entity.User;

import java.util.List;
import java.util.Locale;

/**
 * Politique de mot de passe appliquée à toute création ou modification de mot de passe.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private static final List<String> FORBIDDEN_WORDS = List.of(
            "password", "motdepasse", "compulec", "azerty", "qwerty", "123456", "admin");

    private PasswordPolicy() {
    }

    /**
     * @throws IllegalArgumentException si le mot de passe ne respecte pas la politique
     */
    public static void validate(String password, String confirmation, User user) {
        if (password == null || !password.equals(confirmation)) {
            throw new IllegalArgumentException("Le mot de passe et sa confirmation ne correspondent pas");
        }
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Le mot de passe doit contenir entre " + MIN_LENGTH + " et " + MAX_LENGTH + " caractères");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("Le mot de passe doit contenir au moins une lettre et un chiffre");
        }

        String lower = password.toLowerCase(Locale.ROOT);
        for (String word : FORBIDDEN_WORDS) {
            if (lower.contains(word)) {
                throw new IllegalArgumentException("Le mot de passe contient un mot trop facile à deviner");
            }
        }
        if (user != null) {
            for (String personal : personalWords(user)) {
                if (personal.length() >= 3 && lower.contains(personal)) {
                    throw new IllegalArgumentException(
                            "Le mot de passe ne doit pas contenir votre nom, prénom ou adresse e-mail");
                }
            }
        }
    }

    private static List<String> personalWords(User user) {
        String emailLocal = user.getEmail() == null ? "" : user.getEmail().split("@")[0];
        return List.of(
                lower(user.getNom()),
                lower(user.getPrenom()),
                lower(emailLocal));
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
