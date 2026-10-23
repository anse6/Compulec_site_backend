package com.compulec.backend.exception;

/**
 * Session (refresh token ou jeton 2FA) absente, expirée ou révoquée : l'utilisateur doit se reconnecter.
 */
public class SessionExpiredException extends RuntimeException {
    public SessionExpiredException(String message) {
        super(message);
    }
}
