package com.compulec.backend.controller;

import com.compulec.backend.dto.request.ProjetRequest;
import com.compulec.backend.dto.response.ApiResponse;
import com.compulec.backend.dto.response.ProjetResponse;
import com.compulec.backend.service.ProjetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Contrôleur REST pour la gestion des Projets COMPULEC.
 *
 * IMPORTANT : le champ "date" doit être envoyé au format ISO 8601 : YYYY-MM-DD
 * Exemple : 2025-01-30
 */
@RestController
@RequestMapping("/api/projets")
@RequiredArgsConstructor
@Tag(name = "Projets", description = "Endpoints pour la gestion des projets COMPULEC")
public class ProjetController {

    private final ProjetService projetService;

    // ===================================================================
    // Méthode utilitaire privée : parse la date avec un message clair
    // ===================================================================
    private LocalDate parseDate(String dateStr) {
        try {
            return LocalDate.parse(dateStr.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                "Format de date invalide : '" + dateStr + "'. Veuillez utiliser le format ISO 8601 : YYYY-MM-DD (ex: 2025-01-30)"
            );
        }
    }

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
            @Parameter(description = "Titre du projet", required = true, example = "Projet Alpha")
            @RequestPart("titre") String titre,

            @Parameter(description = "Secteur d'activité", required = true, example = "Informatique")
            @RequestPart("secteur") String secteur,

            @Parameter(description = "Description détaillée du projet", required = true, example = "Description du projet...")
            @RequestPart("description") String description,

            @Parameter(description = "Date du projet au format YYYY-MM-DD", required = true,
                       schema = @Schema(type = "string", format = "date", example = "2025-01-30"))
            @RequestPart("date") String date,

            @Parameter(description = "Localisation du projet", required = true, example = "Douala, Cameroun")
            @RequestPart("localisation") String localisation,

            @Parameter(description = "Images du projet (optionnel, plusieurs fichiers acceptés)")
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        LocalDate parsedDate = parseDate(date);

        ProjetRequest request = ProjetRequest.builder()
                .titre(titre.trim())
                .secteur(secteur.trim())
                .description(description.trim())
                .date(parsedDate)
                .localisation(localisation.trim())
                .build();

        ProjetResponse projet = projetService.createProjet(request, images);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(projet, "Projet créé avec succès ! Il est en attente de publication."));
    }

    // ===================================================================
    // GET /api/projets — Liste de tous les projets (Admin / Manager)
    // ===================================================================
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Liste de tous les projets",
               description = "Récupère tous les projets (publiés et non publiés). Réservé aux Admin et Managers.")
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
               description = "Récupère les informations détaillées d'un projet par son ID.")
    public ResponseEntity<ApiResponse<ProjetResponse>> getProjetById(@PathVariable Long id) {
        ProjetResponse projet = projetService.getProjetById(id);
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

            @Parameter(description = "Titre du projet", required = true, example = "Projet Alpha")
            @RequestPart("titre") String titre,

            @Parameter(description = "Secteur d'activité", required = true, example = "Informatique")
            @RequestPart("secteur") String secteur,

            @Parameter(description = "Description détaillée du projet", required = true, example = "Description du projet...")
            @RequestPart("description") String description,

            @Parameter(description = "Date du projet au format YYYY-MM-DD", required = true,
                       schema = @Schema(type = "string", format = "date", example = "2025-01-30"))
            @RequestPart("date") String date,

            @Parameter(description = "Localisation du projet", required = true, example = "Douala, Cameroun")
            @RequestPart("localisation") String localisation,

            @Parameter(description = "Images du projet (optionnel)")
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        LocalDate parsedDate = parseDate(date);

        ProjetRequest request = ProjetRequest.builder()
                .titre(titre.trim())
                .secteur(secteur.trim())
                .description(description.trim())
                .date(parsedDate)
                .localisation(localisation.trim())
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
}
