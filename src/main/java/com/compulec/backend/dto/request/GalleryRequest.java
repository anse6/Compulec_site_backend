package com.compulec.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GalleryRequest {
    
    @NotNull(message = "L'ID du projet est requis")
    private Long projetId;
    
    private String titre;
    
    private List<String> existingImages;
}
