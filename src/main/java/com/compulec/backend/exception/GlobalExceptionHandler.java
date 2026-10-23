package com.compulec.backend.exception;

import com.compulec.backend.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire global des exceptions REST.
 * Intercepte toutes les exceptions et retourne une réponse JSON standardisée.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Gère les erreurs de validation des champs (@Valid).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.warn("Erreurs de validation : {}", errors);
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error("Données invalides", errors));
    }

    /**
     * Gère la tentative de connexion d'un compte désactivé.
     */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisabledAccount(DisabledException ex) {
        log.warn("Tentative de connexion sur un compte inactif : {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Gère le verrouillage temporaire après trop d'échecs de connexion (423).
     */
    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleLockedAccount(LockedException ex) {
        log.warn("Tentative de connexion sur un compte verrouillé");
        return ResponseEntity
                .status(HttpStatus.LOCKED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Gère les ressources non trouvées (404).
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Ressource non trouvée : {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Gère les erreurs d'accès refusé (403).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Accès refusé : {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Accès refusé : vous n'avez pas les droits nécessaires"));
    }

    /**
     * Gère les erreurs d'authentification incorrectes (401).
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Identifiants incorrects : {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Session expirée, révoquée ou étape 2FA expirée (401).
     */
    @ExceptionHandler(SessionExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleSessionExpired(SessionExpiredException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Tri sur un champ inexistant (?sort=...) (400).
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadSort(PropertyReferenceException ex) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error("Paramètre de tri invalide"));
    }

    /**
     * Fichier trop volumineux (413).
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("Fichier trop volumineux (10 Mo maximum par image)"));
    }

    /**
     * Gère les IllegalArgumentException (400).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Argument illégal : {}", ex.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Action impossible dans l'état actuel (ex. répondre à une conversation fermée) (409).
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex) {
        log.warn("Action refusée : {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * Gère les erreurs de format de date (400).
     * Ex: champ "date" envoyé comme "string" au lieu de "2025-01-30".
     */
    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<ApiResponse<Void>> handleDateTimeParseException(DateTimeParseException ex) {
        log.warn("Format de date invalide : {}", ex.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(
                    "Format de date invalide. Veuillez utiliser le format ISO 8601 : YYYY-MM-DD (exemple : 2025-01-30)"
                ));
    }

    /**
     * Gère toutes les autres exceptions non prévues (500).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Erreur interne du serveur : {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Une erreur interne est survenue. Veuillez réessayer plus tard."));
    }
}
