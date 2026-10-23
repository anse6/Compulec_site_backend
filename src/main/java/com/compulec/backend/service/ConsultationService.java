package com.compulec.backend.service;

import com.compulec.backend.dto.request.ConsultationRequest;
import com.compulec.backend.dto.response.ConsultationResponse;
import com.compulec.backend.entity.Consultation;
import com.compulec.backend.exception.ResourceNotFoundException;
import com.compulec.backend.repository.ConsultationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service de gestion des demandes de consultation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConsultationService {

    private final ConsultationRepository consultationRepository;

    // =====================================================================
    // Créer une nouvelle demande de consultation (accessible publiquement)
    // =====================================================================
    @Transactional
    public ConsultationResponse createConsultation(ConsultationRequest request) {
        Consultation consultation = Consultation.builder()
                .fullName(request.getFullName().trim())
                .company(request.getCompany() != null ? request.getCompany().trim() : null)
                .email(request.getEmail().trim().toLowerCase())
                .phone(request.getPhone().trim())
                .serviceOfInterest(request.getServiceOfInterest() != null ? request.getServiceOfInterest().trim() : null)
                .projectDescription(request.getProjectDescription().trim())
                .preferredContact(request.getPreferredContact() != null ? request.getPreferredContact().trim() : null)
                .lu(false)
                .build();

        consultation = consultationRepository.save(consultation);
        log.info("Nouvelle demande de consultation reçue de : {} ({})", consultation.getFullName(), consultation.getEmail());
        return mapToResponse(consultation);
    }

    // =====================================================================
    // Récupérer toutes les demandes (Admin seulement) avec pagination
    // =====================================================================
    @Transactional(readOnly = true)
    public Page<ConsultationResponse> getAllConsultations(Pageable pageable) {
        return consultationRepository.findAll(pageable).map(this::mapToResponse);
    }

    // =====================================================================
    // Récupérer uniquement les demandes non lues
    // =====================================================================
    @Transactional(readOnly = true)
    public Page<ConsultationResponse> getConsultationsNonLues(Pageable pageable) {
        return consultationRepository.findByLu(false, pageable).map(this::mapToResponse);
    }

    // =====================================================================
    // Compter le nombre de demandes non lues
    // =====================================================================
    @Transactional(readOnly = true)
    public long countConsultationsNonLues() {
        return consultationRepository.countByLu(false);
    }

    // =====================================================================
    // Récupérer une demande par son ID
    // =====================================================================
    @Transactional(readOnly = true)
    public ConsultationResponse getConsultationById(Long id) {
        Consultation consultation = consultationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de consultation", "id", id));
        return mapToResponse(consultation);
    }

    // =====================================================================
    // Marquer une demande comme lue
    // =====================================================================
    @Transactional
    public ConsultationResponse marquerCommeLu(Long id) {
        Consultation consultation = consultationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de consultation", "id", id));

        if (consultation.isLu()) {
            throw new IllegalArgumentException("Cette demande est déjà marquée comme lue.");
        }

        consultation.setLu(true);
        consultation = consultationRepository.save(consultation);
        log.info("Demande de consultation {} marquée comme lue", id);
        return mapToResponse(consultation);
    }

    // =====================================================================
    // Supprimer une demande de consultation
    // =====================================================================
    @Transactional
    public void deleteConsultation(Long id) {
        if (!consultationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Demande de consultation", "id", id);
        }
        consultationRepository.deleteById(id);
        log.info("Demande de consultation {} supprimée", id);
    }

    // =====================================================================
    // Méthode privée : mapper Consultation -> ConsultationResponse
    // =====================================================================
    private ConsultationResponse mapToResponse(Consultation consultation) {
        return ConsultationResponse.builder()
                .id(consultation.getId())
                .fullName(consultation.getFullName())
                .company(consultation.getCompany())
                .email(consultation.getEmail())
                .phone(consultation.getPhone())
                .serviceOfInterest(consultation.getServiceOfInterest())
                .projectDescription(consultation.getProjectDescription())
                .preferredContact(consultation.getPreferredContact())
                .lu(consultation.isLu())
                .createdAt(consultation.getCreatedAt())
                .updatedAt(consultation.getUpdatedAt())
                .build();
    }
}
