package com.compulec.backend.entity;

/**
 * Type d'un jeton de compte à usage unique.
 */
public enum AccountTokenType {
    /** Invitation d'un manager : lien envoyé par e-mail + code transmis par l'admin. */
    INVITATION,
    /** Lien de réinitialisation du mot de passe. */
    PASSWORD_RESET
}
