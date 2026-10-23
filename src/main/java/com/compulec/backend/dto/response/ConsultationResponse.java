package com.compulec.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO retourné pour les requêtes concernant les demandes de consultation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsultationResponse {

    private Long id;
    private String fullName;
    private String company;
    private String email;
    private String phone;
    private String serviceOfInterest;
    private String projectDescription;
    private String preferredContact;
    private boolean lu;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
