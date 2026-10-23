package com.compulec.backend.exception;

/**
 * Action impossible dans l'état actuel de la ressource (réponse HTTP 409).
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
