package com.compulec.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO retourné pour les requêtes concernant les messages de contact.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactResponse {

    private Long id;
    private String nom;
    private String email;
    private String objet;
    private String message;
    private boolean lu;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
