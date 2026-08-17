package com.compulec.backend.dto.response;

import com.compulec.backend.entity.Role;
import com.compulec.backend.entity.Sexe;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO pour la réponse contenant les informations d'un utilisateur.
 * Exclut le mot de passe et les informations sensibles.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String nom;
    private String prenom;
    private String email;
    private String phone;
    private LocalDate dateNaissance;
    private String fonction;
    private Sexe sexe;
    private Role role;
    private boolean actif;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
