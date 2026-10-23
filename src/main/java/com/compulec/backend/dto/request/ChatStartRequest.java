package com.compulec.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour démarrer une nouvelle session de chat.
 * Rempli par le visiteur anonyme avant de commencer à discuter.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatStartRequest {

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 100, message = "Le nom ne peut pas dépasser 100 caractères")
    private String visitorName;

    @Email(message = "Veuillez fournir une adresse email valide")
    @Size(max = 150, message = "L'email ne peut pas dépasser 150 caractères")
    private String visitorEmail;

    /** Langue du site ("fr", "en", "es"…), facultative. */
    @Pattern(regexp = "^[a-zA-Z]{2}$", message = "Code de langue invalide")
    private String language;
}
