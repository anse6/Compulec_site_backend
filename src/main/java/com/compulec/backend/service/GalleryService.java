package com.compulec.backend.service;

import com.compulec.backend.dto.request.GalleryRequest;
import com.compulec.backend.dto.response.GalleryResponse;
import com.compulec.backend.entity.Gallery;
import com.compulec.backend.entity.Projet;
import com.compulec.backend.exception.ResourceNotFoundException;
import com.compulec.backend.repository.GalleryRepository;
import com.compulec.backend.repository.ProjetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GalleryService {

    private final GalleryRepository galleryRepository;
    private final ProjetRepository projetRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public GalleryResponse createGallery(GalleryRequest request, List<MultipartFile> imageFiles) {
        Projet projet = projetRepository.findById(request.getProjetId())
                .orElseThrow(() -> new ResourceNotFoundException("Projet", "id", request.getProjetId()));

        List<String> imageUrls = new ArrayList<>();

        if (imageFiles != null && !imageFiles.isEmpty()) {
            for (MultipartFile file : imageFiles) {
                if (!file.isEmpty()) {
                    String imageUrl = fileStorageService.storeFile(file);
                    imageUrls.add(imageUrl);
                }
            }
        }

        Gallery gallery = Gallery.builder()
                .projet(projet)
                .titre(request.getTitre())
                .images(imageUrls)
                .build();

        gallery = galleryRepository.save(gallery);
        log.info("Nouvelle galerie créée pour le projet '{}' (ID: {})", projet.getTitre(), gallery.getId());
        return mapToResponse(gallery);
    }

    @Transactional
    public GalleryResponse updateGallery(Long id, GalleryRequest request, List<MultipartFile> imageFiles) {
        Gallery gallery = galleryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gallery", "id", id));
        
        Projet projet = projetRepository.findById(request.getProjetId())
                .orElseThrow(() -> new ResourceNotFoundException("Projet", "id", request.getProjetId()));
        
        gallery.setProjet(projet);
        gallery.setTitre(request.getTitre());

        List<String> updatedImages = new ArrayList<>();
        
        // Garder les images existantes spécifiées
        if (request.getExistingImages() != null) {
            updatedImages.addAll(request.getExistingImages());
        }

        // Ajouter les nouvelles images uploadées
        if (imageFiles != null && !imageFiles.isEmpty()) {
            for (MultipartFile file : imageFiles) {
                if (!file.isEmpty()) {
                    String imageUrl = fileStorageService.storeFile(file);
                    updatedImages.add(imageUrl);
                }
            }
        }
        
        gallery.setImages(updatedImages);
        
        gallery = galleryRepository.save(gallery);
        log.info("Galerie mise à jour : '{}' (ID: {})", gallery.getTitre(), gallery.getId());
        return mapToResponse(gallery);
    }

    @Transactional(readOnly = true)
    public List<GalleryResponse> getAllGalleries() {
        return galleryRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GalleryResponse> getGalleriesByProjet(Long projetId) {
        return galleryRepository.findByProjetId(projetId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteGallery(Long id) {
        if (!galleryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Gallery", "id", id);
        }
        galleryRepository.deleteById(id);
        log.info("Galerie supprimée avec l'ID : {}", id);
    }

    private GalleryResponse mapToResponse(Gallery gallery) {
        return GalleryResponse.builder()
                .id(gallery.getId())
                .projetId(gallery.getProjet().getId())
                .projetTitre(gallery.getProjet().getTitre())
                .titre(gallery.getTitre())
                .images(gallery.getImages())
                .createdAt(gallery.getCreatedAt())
                .build();
    }
}
