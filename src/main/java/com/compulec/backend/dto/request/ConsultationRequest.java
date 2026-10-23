package com.compulec.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour l'envoi d'une demande de consultation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsultationRequest {

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 100, message = "Le nom ne peut pas dépasser 100 caractères")
    private String fullName;

    @Size(max = 150, message = "Le nom de l'entreprise ne peut pas dépasser 150 caractères")
    private String company;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Veuillez fournir une adresse email valide")
    @Size(max = 150, message = "L'email ne peut pas dépasser 150 caractères")
    private String email;

    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    @Size(max = 30, message = "Le téléphone ne peut pas dépasser 30 caractères")
    private String phone;

    @Size(max = 150, message = "Le service ne peut pas dépasser 150 caractères")
    private String serviceOfInterest;

    @NotBlank(message = "La description du projet est obligatoire")
    @Size(min = 10, message = "La description doit contenir au moins 10 caractères")
    private String projectDescription;

    @Size(max = 50, message = "La méthode de contact ne peut pas dépasser 50 caractères")
    private String preferredContact;
}
