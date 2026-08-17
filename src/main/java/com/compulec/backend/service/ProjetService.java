package com.compulec.backend.service;

import com.compulec.backend.dto.request.ProjetRequest;
import com.compulec.backend.dto.response.ProjetResponse;
import com.compulec.backend.entity.Projet;
import com.compulec.backend.exception.ResourceNotFoundException;
import com.compulec.backend.repository.ProjetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * Service de gestion des Projets.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjetService {

    private final ProjetRepository projetRepository;
    private final FileStorageService fileStorageService;

    /**
     * Crée un nouveau projet (non publié par défaut).
     * Accepte optionnellement une liste de fichiers images à téléverser.
     */
    @Transactional
    public ProjetResponse createProjet(ProjetRequest request, List<MultipartFile> imageFiles) {
        List<String> imageUrls = new ArrayList<>();

        // Stocker les images uploadées si présentes
        if (imageFiles != null && !imageFiles.isEmpty()) {
            for (MultipartFile file : imageFiles) {
                if (!file.isEmpty()) {
                    String imageUrl = fileStorageService.storeFile(file);
                    imageUrls.add(imageUrl);
                }
            }
        }

        // Ajouter également les URLs directes si fournies dans la requête JSON
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            imageUrls.addAll(request.getImages());
        }

        Projet projet = Projet.builder()
                .titre(request.getTitre())
                .secteur(request.getSecteur())
                .description(request.getDescription())
                .date(request.getDate())
                .localisation(request.getLocalisation())
                .images(imageUrls)
                .publie(false) // Non publié par défaut
                .build();

        projet = projetRepository.save(projet);
        log.info("Nouveau projet créé : '{}' (ID: {})", projet.getTitre(), projet.getId());
        return mapToResponse(projet);
    }

    /**
     * Récupère tous les projets avec pagination.
     */
    @Transactional(readOnly = true)
    public Page<ProjetResponse> getAllProjets(Pageable pageable) {
        return projetRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Récupère uniquement les projets publiés.
     */
    @Transactional(readOnly = true)
    public Page<ProjetResponse> getProjetsPublies(Pageable pageable) {
        return projetRepository.findByPublie(true, pageable).map(this::mapToResponse);
    }

    /**
     * Récupère un projet par son ID.
     */
    @Transactional(readOnly = true)
    public ProjetResponse getProjetById(Long id) {
        Projet projet = projetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Projet", "id", id));
        return mapToResponse(projet);
    }

    /**
     * Met à jour les informations d'un projet.
     */
    @Transactional
    public ProjetResponse updateProjet(Long id, ProjetRequest request, List<MultipartFile> imageFiles) {
        Projet projet = projetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Projet", "id", id));

        List<String> imageUrls = new ArrayList<>();

        // Stocker les nouvelles images uploadées
        if (imageFiles != null && !imageFiles.isEmpty()) {
            for (MultipartFile file : imageFiles) {
                if (!file.isEmpty()) {
                    String imageUrl = fileStorageService.storeFile(file);
                    imageUrls.add(imageUrl);
                }
            }
        }

        // Ajouter les URLs directes si présentes
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            imageUrls.addAll(request.getImages());
        }

        projet.setTitre(request.getTitre());
        projet.setSecteur(request.getSecteur());
        projet.setDescription(request.getDescription());
        projet.setDate(request.getDate());
        projet.setLocalisation(request.getLocalisation());

        if (!imageUrls.isEmpty()) {
            projet.setImages(imageUrls);
        }

        projet = projetRepository.save(projet);
        log.info("Projet mis à jour : '{}' (ID: {})", projet.getTitre(), projet.getId());
        return mapToResponse(projet);
    }

    /**
     * Publie un projet (change le statut de publie à true).
     */
    @Transactional
    public ProjetResponse publierProjet(Long id) {
        Projet projet = projetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Projet", "id", id));

        if (projet.isPublie()) {
            throw new IllegalArgumentException("Ce projet est déjà publié.");
        }

        projet.setPublie(true);
        projet = projetRepository.save(projet);
        log.info("Projet publié : '{}' (ID: {})", projet.getTitre(), projet.getId());
        return mapToResponse(projet);
    }

    /**
     * Dépublie un projet (passe de publié à non publié).
     */
    @Transactional
    public ProjetResponse depublierProjet(Long id) {
        Projet projet = projetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Projet", "id", id));

        if (!projet.isPublie()) {
            throw new IllegalArgumentException("Ce projet n'est pas encore publié.");
        }

        projet.setPublie(false);
        projet = projetRepository.save(projet);
        log.info("Projet dépublié : '{}' (ID: {})", projet.getTitre(), projet.getId());
        return mapToResponse(projet);
    }

    /**
     * Supprime définitivement un projet.
     */
    @Transactional
    public void deleteProjet(Long id) {
        if (!projetRepository.existsById(id)) {
            throw new ResourceNotFoundException("Projet", "id", id);
        }
        projetRepository.deleteById(id);
        log.info("Projet supprimé avec l'ID : {}", id);
    }

    // ===== Méthodes privées =====

    private ProjetResponse mapToResponse(Projet projet) {
        return ProjetResponse.builder()
                .id(projet.getId())
                .titre(projet.getTitre())
                .secteur(projet.getSecteur())
                .description(projet.getDescription())
                .date(projet.getDate())
                .images(projet.getImages())
                .localisation(projet.getLocalisation())
                .publie(projet.isPublie())
                .createdAt(projet.getCreatedAt())
                .updatedAt(projet.getUpdatedAt())
                .build();
    }
}
