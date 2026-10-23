package com.compulec.backend.controller;

import com.compulec.backend.dto.request.ConsultationRequest;
import com.compulec.backend.dto.response.ApiResponse;
import com.compulec.backend.dto.response.ConsultationResponse;
import com.compulec.backend.service.ConsultationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Contrôleur REST pour les demandes de Consultation COMPULEC.
 *
 * Accès public  : POST /api/consultations
 * Accès protégé : tous les autres endpoints (Admin ou Manager seulement)
 */
@RestController
@RequestMapping("/api/consultations")
@RequiredArgsConstructor
@Tag(name = "Consultation", description = "Endpoints pour la gestion des demandes de consultation COMPULEC")
public class ConsultationController {

    private final ConsultationService consultationService;

    // ===================================================================
    // POST /api/consultations — Envoyer une demande (accès public)
    // ===================================================================
    @PostMapping
    @Operation(
            summary = "Envoyer une demande de consultation",
            description = "Accessible sans authentification. Permet à n'importe qui de demander une consultation."
    )
    public ResponseEntity<ApiResponse<ConsultationResponse>> createConsultation(
            @Valid @RequestBody ConsultationRequest request
    ) {
        ConsultationResponse consultation = consultationService.createConsultation(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(consultation,
                        "Votre demande de consultation a été envoyée avec succès ! Notre équipe vous recontactera bientôt."));
    }

    // ===================================================================
    // GET /api/consultations — Liste de toutes les demandes (Admin / Manager)
    // ===================================================================
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Liste de toutes les demandes de consultation",
            description = "Récupère toutes les demandes avec pagination. Réservé aux Admin et Managers."
    )
    public ResponseEntity<ApiResponse<Page<ConsultationResponse>>> getAllConsultations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sort
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sort));
        Page<ConsultationResponse> consultations = consultationService.getAllConsultations(pageable);
        return ResponseEntity.ok(ApiResponse.success(consultations));
    }

    // ===================================================================
    // GET /api/consultations/non-lues — Demandes non lues (Admin / Manager)
    // ===================================================================
    @GetMapping("/non-lues")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Demandes de consultation non lues",
            description = "Récupère uniquement les demandes non encore lues."
    )
    public ResponseEntity<ApiResponse<Page<ConsultationResponse>>> getConsultationsNonLues(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ConsultationResponse> consultations = consultationService.getConsultationsNonLues(pageable);
        return ResponseEntity.ok(ApiResponse.success(consultations));
    }

    // ===================================================================
    // GET /api/consultations/count-non-lues — Nombre de demandes non lues
    // ===================================================================
    @GetMapping("/count-non-lues")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Nombre de demandes non lues",
            description = "Retourne le nombre total de demandes non lues pour les badges de notification."
    )
    public ResponseEntity<ApiResponse<Map<String, Long>>> countConsultationsNonLues() {
        long count = consultationService.countConsultationsNonLues();
        return ResponseEntity.ok(ApiResponse.success(Map.of("nonLus", count)));
    }

    // ===================================================================
    // GET /api/consultations/{id} — Détail d'une demande (Admin / Manager)
    // ===================================================================
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Détail d'une demande de consultation",
            description = "Récupère les informations d'une demande par son ID."
    )
    public ResponseEntity<ApiResponse<ConsultationResponse>> getConsultationById(@PathVariable Long id) {
        ConsultationResponse consultation = consultationService.getConsultationById(id);
        return ResponseEntity.ok(ApiResponse.success(consultation));
    }

    // ===================================================================
    // PATCH /api/consultations/{id}/lire — Marquer comme lu (Admin / Manager)
    // ===================================================================
    @PatchMapping("/{id}/lire")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Marquer une demande comme lue",
            description = "Change le statut d'une demande de 'non lu' à 'lu'."
    )
    public ResponseEntity<ApiResponse<ConsultationResponse>> marquerCommeLu(@PathVariable Long id) {
        ConsultationResponse consultation = consultationService.marquerCommeLu(id);
        return ResponseEntity.ok(ApiResponse.success(consultation, "Demande marquée comme lue."));
    }

    // ===================================================================
    // DELETE /api/consultations/{id} — Supprimer une demande (Admin seulement)
    // ===================================================================
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Supprimer une demande de consultation",
            description = "Supprime définitivement une demande. Réservé aux Administrateurs."
    )
    public ResponseEntity<ApiResponse<Void>> deleteConsultation(@PathVariable Long id) {
        consultationService.deleteConsultation(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Demande de consultation supprimée avec succès."));
    }
}
