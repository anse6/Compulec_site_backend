package com.compulec.backend.controller;

import com.compulec.backend.dto.request.ProjetRequest;
import com.compulec.backend.dto.response.ApiResponse;
import com.compulec.backend.dto.response.ProjetResponse;
import com.compulec.backend.service.ProjetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.compulec.backend.exception.ResourceNotFoundException;
import com.compulec.backend.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


/**
 * Contrôleur REST pour la gestion des Projets COMPULEC.
 */
@RestController
@RequestMapping("/projets")
@RequiredArgsConstructor
@Tag(name = "Projets", description = "Endpoints pour la gestion des projets COMPULEC")
public class ProjetController {

    private final ProjetService projetService;

    // ===================================================================
    // POST /api/projets — Créer un projet
    // ===================================================================
    @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Créer un projet",
            description = "Crée un nouveau projet (statut : non publié par défaut). Supporte l'upload de 1 ou plusieurs images. " +
                          "**Format multipart/form-data**. Le champ `date` doit être au format `YYYY-MM-DD` (ex: `2025-01-30`)."
    )
    public ResponseEntity<ApiResponse<ProjetResponse>> createProjet(
            @Parameter(description = "Titre du projet", required = true) @RequestPart("titre") String titre,
            @Parameter(description = "Sous-titre") @RequestPart(value = "sousTitre", required = false) String sousTitre,
            @Parameter(description = "Catégorie", required = true) @RequestPart("categorie") String categorie,
            @Parameter(description = "Service") @RequestPart(value = "service", required = false) String service,
            @Parameter(description = "Nom du client") @RequestPart(value = "clientName", required = false) String clientName,
            @Parameter(description = "Secteur", required = true) @RequestPart("secteur") String secteur,
            @Parameter(description = "Localisation", required = true) @RequestPart("localisation") String localisation,
            @Parameter(description = "Latitude GPS (carte)") @RequestPart(value = "latitude", required = false) String latitude,
            @Parameter(description = "Longitude GPS (carte)") @RequestPart(value = "longitude", required = false) String longitude,
            @Parameter(description = "Status") @RequestPart(value = "status", required = false) String status,
            @Parameter(description = "Overview", required = true) @RequestPart("overview") String overview,
            @Parameter(description = "Contexte", required = true) @RequestPart("contexte") String contexte,
            @Parameter(description = "Besoins") @RequestParam(value = "besoins", required = false) List<String> besoins,
            @Parameter(description = "Solution", required = true) @RequestPart("solution") String solution,
            @Parameter(description = "Technologies") @RequestParam(value = "technologies", required = false) List<String> technologies,
            @Parameter(description = "Résultat", required = true) @RequestPart("resultat") String resultat,
            @Parameter(description = "Images du projet") @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        ProjetRequest request = ProjetRequest.builder()
                .titre(titre != null ? titre.trim() : null)
                .sousTitre(sousTitre != null ? sousTitre.trim() : null)
                .categorie(categorie != null ? categorie.trim() : null)
                .service(service != null ? service.trim() : null)
                .clientName(clientName != null ? clientName.trim() : null)
                .secteur(secteur != null ? secteur.trim() : null)
                .localisation(localisation != null ? localisation.trim() : null)
                .latitude(parseCoordinate(latitude, 90))
                .longitude(parseCoordinate(longitude, 180))
                .status(status != null ? status.trim() : null)
                .overview(overview != null ? overview.trim() : null)
                .contexte(contexte != null ? contexte.trim() : null)
                .besoins(besoins)
                .solution(solution != null ? solution.trim() : null)
                .technologies(technologies)
                .resultat(resultat != null ? resultat.trim() : null)
                .build();

        ProjetResponse projet = projetService.createProjet(request, images);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(projet, "Projet créé avec succès ! Il est en attente de publication."));
    }

    // ===================================================================
    // GET /api/projets — Liste de tous les projets
    // ===================================================================
    @GetMapping
    @Operation(summary = "Liste de tous les projets",
               description = "Récupère tous les projets (publiés et non publiés). Réservé aux ADMIN / MANAGER.")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Page<ProjetResponse>>> getAllProjets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sort
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sort));
        Page<ProjetResponse> projets = projetService.getAllProjets(pageable);
        return ResponseEntity.ok(ApiResponse.success(projets));
    }

    // ===================================================================
    // GET /api/projets/publies — Projets publiés (accès public)
    // ===================================================================
    @GetMapping("/publies")
    @Operation(summary = "Projets publiés (accès public)",
               description = "Récupère uniquement les projets dont le statut est 'publié'. Accessible sans authentification.")
    public ResponseEntity<ApiResponse<Page<ProjetResponse>>> getProjetsPublies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ProjetResponse> projets = projetService.getProjetsPublies(pageable);
        return ResponseEntity.ok(ApiResponse.success(projets));
    }

    // ===================================================================
    // GET /api/projets/{id} — Détail d'un projet
    // ===================================================================
    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un projet",
               description = "Récupère un projet par son ID. Un brouillon n'est visible que par un ADMIN ou MANAGER connecté.")
    public ResponseEntity<ApiResponse<ProjetResponse>> getProjetById(@PathVariable Long id) {
        ProjetResponse projet = projetService.getProjetById(id);
        if (!projet.isPublie() && !CurrentUser.isStaff()) {
            throw new ResourceNotFoundException("Projet", "id", id);
        }
        return ResponseEntity.ok(ApiResponse.success(projet));
    }

    // ===================================================================
    // PUT /api/projets/{id} — Modifier un projet
    // ===================================================================
    @PutMapping(value = "/{id}", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Modifier un projet",
               description = "Met à jour toutes les informations d'un projet. " +
                             "Le champ `date` doit être au format `YYYY-MM-DD`. " +
                             "Les nouvelles images envoyées remplacent les anciennes.")
    public ResponseEntity<ApiResponse<ProjetResponse>> updateProjet(
            @PathVariable Long id,
            @Parameter(description = "Titre du projet", required = true) @RequestPart("titre") String titre,
            @Parameter(description = "Sous-titre") @RequestPart(value = "sousTitre", required = false) String sousTitre,
            @Parameter(description = "Catégorie", required = true) @RequestPart("categorie") String categorie,
            @Parameter(description = "Service") @RequestPart(value = "service", required = false) String service,
            @Parameter(description = "Nom du client") @RequestPart(value = "clientName", required = false) String clientName,
            @Parameter(description = "Secteur", required = true) @RequestPart("secteur") String secteur,
            @Parameter(description = "Localisation", required = true) @RequestPart("localisation") String localisation,
            @Parameter(description = "Latitude GPS (carte)") @RequestPart(value = "latitude", required = false) String latitude,
            @Parameter(description = "Longitude GPS (carte)") @RequestPart(value = "longitude", required = false) String longitude,
            @Parameter(description = "Status") @RequestPart(value = "status", required = false) String status,
            @Parameter(description = "Overview", required = true) @RequestPart("overview") String overview,
            @Parameter(description = "Contexte", required = true) @RequestPart("contexte") String contexte,
            @Parameter(description = "Besoins") @RequestParam(value = "besoins", required = false) List<String> besoins,
            @Parameter(description = "Solution", required = true) @RequestPart("solution") String solution,
            @Parameter(description = "Technologies") @RequestParam(value = "technologies", required = false) List<String> technologies,
            @Parameter(description = "Résultat", required = true) @RequestPart("resultat") String resultat,
            @Parameter(description = "Images du projet") @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        ProjetRequest request = ProjetRequest.builder()
                .titre(titre != null ? titre.trim() : null)
                .sousTitre(sousTitre != null ? sousTitre.trim() : null)
                .categorie(categorie != null ? categorie.trim() : null)
                .service(service != null ? service.trim() : null)
                .clientName(clientName != null ? clientName.trim() : null)
                .secteur(secteur != null ? secteur.trim() : null)
                .localisation(localisation != null ? localisation.trim() : null)
                .latitude(parseCoordinate(latitude, 90))
                .longitude(parseCoordinate(longitude, 180))
                .status(status != null ? status.trim() : null)
                .overview(overview != null ? overview.trim() : null)
                .contexte(contexte != null ? contexte.trim() : null)
                .besoins(besoins)
                .solution(solution != null ? solution.trim() : null)
                .technologies(technologies)
                .resultat(resultat != null ? resultat.trim() : null)
                .build();

        ProjetResponse projet = projetService.updateProjet(id, request, images);
        return ResponseEntity.ok(ApiResponse.success(projet, "Projet mis à jour avec succès !"));
    }

    // ===================================================================
    // PATCH /api/projets/{id}/publier — Publier un projet
    // ===================================================================
    @PatchMapping("/{id}/publier")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Publier un projet",
               description = "Change le statut du projet de 'non publié' à 'publié'. Le projet devient visible publiquement.")
    public ResponseEntity<ApiResponse<ProjetResponse>> publierProjet(@PathVariable Long id) {
        ProjetResponse projet = projetService.publierProjet(id);
        return ResponseEntity.ok(ApiResponse.success(projet, "Projet publié avec succès ! Il est maintenant visible publiquement."));
    }

    // ===================================================================
    // PATCH /api/projets/{id}/depublier — Dépublier un projet
    // ===================================================================
    @PatchMapping("/{id}/depublier")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Dépublier un projet",
               description = "Retire un projet de la publication. Il ne sera plus visible publiquement.")
    public ResponseEntity<ApiResponse<ProjetResponse>> depublierProjet(@PathVariable Long id) {
        ProjetResponse projet = projetService.depublierProjet(id);
        return ResponseEntity.ok(ApiResponse.success(projet, "Projet retiré de la publication."));
    }

    // ===================================================================
    // DELETE /api/projets/{id} — Supprimer un projet (Admin only)
    // ===================================================================
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Supprimer un projet",
               description = "Supprime définitivement un projet. Réservé aux Administrateurs.")
    public ResponseEntity<ApiResponse<Void>> deleteProjet(@PathVariable Long id) {
        projetService.deleteProjet(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Projet supprimé avec succès."));
    }

    /**
     * Coordonnée GPS facultative : vide → null ; hors bornes ou invalide → 400.
     */
    private static Double parseCoordinate(String raw, double max) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            double value = Double.parseDouble(raw.trim());
            if (Double.isNaN(value) || Math.abs(value) > max) {
                throw new IllegalArgumentException("Coordonnée GPS hors limites : " + raw);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Coordonnée GPS invalide : " + raw);
        }
    }
}
