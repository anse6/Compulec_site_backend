package com.compulec.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour l'envoi d'un message de contact.
 * Champs alignés avec le formulaire frontend ContactPage.jsx
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactRequest {

    /** Nom complet (fullName) */
    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 100, message = "Le nom ne peut pas dépasser 100 caractères")
    private String nom;

    /** Entreprise / Organisation (company) — optionnel */
    @Size(max = 150, message = "Le nom de l'entreprise ne peut pas dépasser 150 caractères")
    private String entreprise;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Veuillez fournir une adresse email valide")
    @Size(max = 150, message = "L'email ne peut pas dépasser 150 caractères")
    private String email;

    /** Numéro de téléphone (phone) */
    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    @Size(max = 30, message = "Le téléphone ne peut pas dépasser 30 caractères")
    private String telephone;

    /** Objet du message (subject) */
    @NotBlank(message = "L'objet est obligatoire")
    @Size(max = 200, message = "L'objet ne peut pas dépasser 200 caractères")
    private String objet;

    @NotBlank(message = "Le message est obligatoire")
    @Size(min = 10, message = "Le message doit contenir au moins 10 caractères")
    private String message;
}
