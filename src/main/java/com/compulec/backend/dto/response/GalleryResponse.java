package com.compulec.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GalleryResponse {
    private Long id;
    private Long projetId;
    private String projetTitre;
    private String titre;
    private List<String> images;
    private LocalDateTime createdAt;
}
