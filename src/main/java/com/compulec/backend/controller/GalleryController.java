package com.compulec.backend.controller;

import com.compulec.backend.dto.request.GalleryRequest;
import com.compulec.backend.dto.response.ApiResponse;
import com.compulec.backend.dto.response.GalleryResponse;
import com.compulec.backend.service.GalleryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/galleries")
@RequiredArgsConstructor
public class GalleryController {

    private final GalleryService galleryService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<GalleryResponse>> createGallery(
            @ModelAttribute GalleryRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        
        GalleryResponse response = galleryService.createGallery(request, images);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Galerie ajoutée avec succès"));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<GalleryResponse>> updateGallery(
            @PathVariable Long id,
            @ModelAttribute GalleryRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        
        GalleryResponse response = galleryService.updateGallery(id, request, images);
        return ResponseEntity.ok(ApiResponse.success(response, "Galerie mise à jour avec succès"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<GalleryResponse>>> getAllGalleries() {
        List<GalleryResponse> galleries = galleryService.getAllGalleries();
        return ResponseEntity.ok(ApiResponse.success(galleries));
    }

    @GetMapping("/projet/{projetId}")
    public ResponseEntity<ApiResponse<List<GalleryResponse>>> getGalleriesByProjet(@PathVariable Long projetId) {
        List<GalleryResponse> galleries = galleryService.getGalleriesByProjet(projetId);
        return ResponseEntity.ok(ApiResponse.success(galleries));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteGallery(@PathVariable Long id) {
        galleryService.deleteGallery(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Galerie supprimée avec succès"));
    }
}
