package com.compulec.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleResponse {
    private Long id;
    private String title;
    private String subtitle;
    private String heading;
    private String overview;
    private String imageUrl;
    private boolean published;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
