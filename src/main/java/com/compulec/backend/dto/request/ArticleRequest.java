package com.compulec.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleRequest {
    private String title;
    private String subtitle;
    private String heading;
    private String overview;
    private Boolean published;
}
