package com.compulec.backend.controller;

import com.compulec.backend.dto.request.ContactRequest;
import com.compulec.backend.dto.response.ApiResponse;
import com.compulec.backend.dto.response.ContactResponse;
import com.compulec.backend.service.ContactService;
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
 * Contrôleur REST pour les messages de Contact COMPULEC.
 *
 * Accès public  : POST /api/contacts  (n'importe qui peut envoyer un message)
 * Accès protégé : tous les autres endpoints (Admin ou Manager seulement)
 */
@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
@Tag(name = "Contact", description = "Endpoints pour la gestion des messages de contact COMPULEC")
public class ContactController {

    private final ContactService contactService;

    // ===================================================================
    // POST /api/contacts — Envoyer un message de contact (accès public)
    // ===================================================================
    @PostMapping
    @Operation(
            summary = "Envoyer un message de contact",
            description = "Accessible sans authentification. Permet à n'importe qui d'envoyer un message à COMPULEC."
    )
    public ResponseEntity<ApiResponse<ContactResponse>> createContact(
            @Valid @RequestBody ContactRequest request
    ) {
        ContactResponse contact = contactService.createContact(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(contact,
                        "Votre message a été envoyé avec succès ! L'équipe COMPULEC vous répondra dans les meilleurs délais."));
    }

    // ===================================================================
    // GET /api/contacts — Liste de tous les messages (Admin / Manager)
    // ===================================================================
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Liste de tous les messages de contact",
            description = "Récupère tous les messages de contact avec pagination. Réservé aux Admin et Managers."
    )
    public ResponseEntity<ApiResponse<Page<ContactResponse>>> getAllContacts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sort
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sort));
        Page<ContactResponse> contacts = contactService.getAllContacts(pageable);
        return ResponseEntity.ok(ApiResponse.success(contacts));
    }

    // ===================================================================
    // GET /api/contacts/non-lus — Messages non lus (Admin / Manager)
    // ===================================================================
    @GetMapping("/non-lus")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Messages non lus",
            description = "Récupère uniquement les messages de contact non encore lus."
    )
    public ResponseEntity<ApiResponse<Page<ContactResponse>>> getContactsNonLus(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ContactResponse> contacts = contactService.getContactsNonLus(pageable);
        return ResponseEntity.ok(ApiResponse.success(contacts));
    }

    // ===================================================================
    // GET /api/contacts/count-non-lus — Nombre de messages non lus
    // ===================================================================
    @GetMapping("/count-non-lus")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Nombre de messages non lus",
            description = "Retourne le nombre total de messages de contact non lus. Utile pour afficher un badge de notification."
    )
    public ResponseEntity<ApiResponse<Map<String, Long>>> countContactsNonLus() {
        long count = contactService.countContactsNonLus();
        return ResponseEntity.ok(ApiResponse.success(Map.of("nonLus", count)));
    }

    // ===================================================================
    // GET /api/contacts/{id} — Détail d'un message (Admin / Manager)
    // ===================================================================
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Détail d'un message de contact",
            description = "Récupère les informations d'un message de contact par son ID."
    )
    public ResponseEntity<ApiResponse<ContactResponse>> getContactById(@PathVariable Long id) {
        ContactResponse contact = contactService.getContactById(id);
        return ResponseEntity.ok(ApiResponse.success(contact));
    }

    // ===================================================================
    // PATCH /api/contacts/{id}/lire — Marquer comme lu (Admin / Manager)
    // ===================================================================
    @PatchMapping("/{id}/lire")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Marquer un message comme lu",
            description = "Change le statut d'un message de contact de 'non lu' à 'lu'."
    )
    public ResponseEntity<ApiResponse<ContactResponse>> marquerCommeLu(@PathVariable Long id) {
        ContactResponse contact = contactService.marquerCommeLu(id);
        return ResponseEntity.ok(ApiResponse.success(contact, "Message marqué comme lu."));
    }

    // ===================================================================
    // PUT /api/contacts/{id} — Modifier un message (Admin seulement)
    // ===================================================================
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Modifier un message de contact",
            description = "Permet à l'Administrateur de modifier le contenu d'un message de contact."
    )
    public ResponseEntity<ApiResponse<ContactResponse>> updateContact(
            @PathVariable Long id,
            @Valid @RequestBody ContactRequest request
    ) {
        ContactResponse contact = contactService.updateContact(id, request);
        return ResponseEntity.ok(ApiResponse.success(contact, "Message de contact mis à jour avec succès !"));
    }

    // ===================================================================
    // DELETE /api/contacts/{id} — Supprimer un message (Admin seulement)
    // ===================================================================
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Supprimer un message de contact",
            description = "Supprime définitivement un message de contact. Réservé aux Administrateurs."
    )
    public ResponseEntity<ApiResponse<Void>> deleteContact(@PathVariable Long id) {
        contactService.deleteContact(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Message de contact supprimé avec succès."));
    }
}
