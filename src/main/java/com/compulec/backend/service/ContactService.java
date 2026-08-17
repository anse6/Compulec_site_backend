package com.compulec.backend.service;

import com.compulec.backend.dto.request.ContactRequest;
import com.compulec.backend.dto.response.ContactResponse;
import com.compulec.backend.entity.Contact;
import com.compulec.backend.exception.ResourceNotFoundException;
import com.compulec.backend.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service de gestion des messages de contact.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContactService {

    private final ContactRepository contactRepository;

    // =====================================================================
    // Créer un nouveau message de contact (accessible publiquement)
    // =====================================================================
    @Transactional
    public ContactResponse createContact(ContactRequest request) {
        Contact contact = Contact.builder()
                .nom(request.getNom().trim())
                .email(request.getEmail().trim().toLowerCase())
                .objet(request.getObjet().trim())
                .message(request.getMessage().trim())
                .lu(false)
                .build();

        contact = contactRepository.save(contact);
        log.info("Nouveau message de contact reçu de : {} ({})", contact.getNom(), contact.getEmail());
        return mapToResponse(contact);
    }

    // =====================================================================
    // Récupérer tous les messages (Admin seulement) avec pagination
    // =====================================================================
    @Transactional(readOnly = true)
    public Page<ContactResponse> getAllContacts(Pageable pageable) {
        return contactRepository.findAll(pageable).map(this::mapToResponse);
    }

    // =====================================================================
    // Récupérer uniquement les messages non lus
    // =====================================================================
    @Transactional(readOnly = true)
    public Page<ContactResponse> getContactsNonLus(Pageable pageable) {
        return contactRepository.findByLu(false, pageable).map(this::mapToResponse);
    }

    // =====================================================================
    // Compter le nombre de messages non lus
    // =====================================================================
    @Transactional(readOnly = true)
    public long countContactsNonLus() {
        return contactRepository.countByLu(false);
    }

    // =====================================================================
    // Récupérer un message par son ID
    // =====================================================================
    @Transactional(readOnly = true)
    public ContactResponse getContactById(Long id) {
        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Message de contact", "id", id));
        return mapToResponse(contact);
    }

    // =====================================================================
    // Marquer un message comme lu
    // =====================================================================
    @Transactional
    public ContactResponse marquerCommeLu(Long id) {
        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Message de contact", "id", id));

        if (contact.isLu()) {
            throw new IllegalArgumentException("Ce message est déjà marqué comme lu.");
        }

        contact.setLu(true);
        contact = contactRepository.save(contact);
        log.info("Message de contact {} marqué comme lu", id);
        return mapToResponse(contact);
    }

    // =====================================================================
    // Modifier un message de contact (Admin peut corriger / mettre à jour)
    // =====================================================================
    @Transactional
    public ContactResponse updateContact(Long id, ContactRequest request) {
        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Message de contact", "id", id));

        contact.setNom(request.getNom().trim());
        contact.setEmail(request.getEmail().trim().toLowerCase());
        contact.setObjet(request.getObjet().trim());
        contact.setMessage(request.getMessage().trim());

        contact = contactRepository.save(contact);
        log.info("Message de contact {} mis à jour", id);
        return mapToResponse(contact);
    }

    // =====================================================================
    // Supprimer un message de contact
    // =====================================================================
    @Transactional
    public void deleteContact(Long id) {
        if (!contactRepository.existsById(id)) {
            throw new ResourceNotFoundException("Message de contact", "id", id);
        }
        contactRepository.deleteById(id);
        log.info("Message de contact {} supprimé", id);
    }

    // =====================================================================
    // Méthode privée : mapper Contact -> ContactResponse
    // =====================================================================
    private ContactResponse mapToResponse(Contact contact) {
        return ContactResponse.builder()
                .id(contact.getId())
                .nom(contact.getNom())
                .email(contact.getEmail())
                .objet(contact.getObjet())
                .message(contact.getMessage())
                .lu(contact.isLu())
                .createdAt(contact.getCreatedAt())
                .updatedAt(contact.getUpdatedAt())
                .build();
    }
}
