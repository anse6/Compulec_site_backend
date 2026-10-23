package com.compulec.backend.controller;

import com.compulec.backend.dto.request.ArticleRequest;
import com.compulec.backend.dto.response.ApiResponse;
import com.compulec.backend.dto.response.ArticleResponse;
import com.compulec.backend.service.ArticleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.compulec.backend.exception.ResourceNotFoundException;
import com.compulec.backend.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/articles")
@RequiredArgsConstructor
@Tag(name = "Articles", description = "Endpoints pour la gestion des actualités/articles COMPULEC")
public class ArticleController {

    private final ArticleService articleService;

    @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Créer un article", description = "Crée un nouvel article (News).")
    public ResponseEntity<ApiResponse<ArticleResponse>> createArticle(
            @Parameter(description = "Titre") @RequestPart("title") String title,
            @Parameter(description = "Sous-titre") @RequestPart(value = "subtitle", required = false) String subtitle,
            @Parameter(description = "Heading") @RequestPart(value = "heading", required = false) String heading,
            @Parameter(description = "Overview") @RequestPart(value = "overview", required = false) String overview,
            @Parameter(description = "Publié") @RequestPart(value = "published", required = false) String publishedStr,
            @Parameter(description = "Image uploadée") @RequestPart(value = "imageFile", required = false) MultipartFile imageFile,
            @Parameter(description = "URL de l'image (si pas d'upload)") @RequestPart(value = "imageUrl", required = false) String imageUrl
    ) {
        Boolean published = publishedStr != null ? Boolean.parseBoolean(publishedStr) : false;
        
        ArticleRequest request = ArticleRequest.builder()
                .title(title != null ? title.trim() : null)
                .subtitle(subtitle != null ? subtitle.trim() : null)
                .heading(heading != null ? heading.trim() : null)
                .overview(overview != null ? overview.trim() : null)
                .published(published)
                .build();

        ArticleResponse article = articleService.createArticle(request, imageFile, imageUrl);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(article, "Article créé avec succès !"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Liste de tous les articles", description = "Accès public à tous les articles")
    public ResponseEntity<ApiResponse<Page<ArticleResponse>>> getAllArticles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sort
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sort));
        Page<ArticleResponse> articles = articleService.getAllArticles(pageable);
        return ResponseEntity.ok(ApiResponse.success(articles));
    }

    @GetMapping("/publies")
    @Operation(summary = "Articles publiés", description = "Accès public aux articles publiés")
    public ResponseEntity<ApiResponse<Page<ArticleResponse>>> getArticlesPublies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ArticleResponse> articles = articleService.getArticlesPublies(pageable);
        return ResponseEntity.ok(ApiResponse.success(articles));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un article", description = "Un brouillon n'est visible que par un ADMIN ou MANAGER connecté")
    public ResponseEntity<ApiResponse<ArticleResponse>> getArticleById(@PathVariable Long id) {
        ArticleResponse article = articleService.getArticleById(id);
        if (!article.isPublished() && !CurrentUser.isStaff()) {
            throw new ResourceNotFoundException("Article", "id", id);
        }
        return ResponseEntity.ok(ApiResponse.success(article));
    }

    @PutMapping(value = "/{id}", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Modifier un article")
    public ResponseEntity<ApiResponse<ArticleResponse>> updateArticle(
            @PathVariable Long id,
            @Parameter(description = "Titre") @RequestPart(value = "title", required = false) String title,
            @Parameter(description = "Sous-titre") @RequestPart(value = "subtitle", required = false) String subtitle,
            @Parameter(description = "Heading") @RequestPart(value = "heading", required = false) String heading,
            @Parameter(description = "Overview") @RequestPart(value = "overview", required = false) String overview,
            @Parameter(description = "Publié") @RequestPart(value = "published", required = false) String publishedStr,
            @Parameter(description = "Image uploadée") @RequestPart(value = "imageFile", required = false) MultipartFile imageFile,
            @Parameter(description = "URL de l'image (si pas d'upload)") @RequestPart(value = "imageUrl", required = false) String imageUrl
    ) {
        Boolean published = publishedStr != null ? Boolean.parseBoolean(publishedStr) : null;

        ArticleRequest request = ArticleRequest.builder()
                .title(title != null ? title.trim() : null)
                .subtitle(subtitle != null ? subtitle.trim() : null)
                .heading(heading != null ? heading.trim() : null)
                .overview(overview != null ? overview.trim() : null)
                .published(published)
                .build();

        ArticleResponse article = articleService.updateArticle(id, request, imageFile, imageUrl);
        return ResponseEntity.ok(ApiResponse.success(article, "Article mis à jour avec succès !"));
    }

    @PatchMapping("/{id}/publier")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Publier un article")
    public ResponseEntity<ApiResponse<ArticleResponse>> publierArticle(@PathVariable Long id) {
        ArticleResponse article = articleService.publierArticle(id);
        return ResponseEntity.ok(ApiResponse.success(article, "Article publié avec succès !"));
    }

    @PatchMapping("/{id}/depublier")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Dépublier un article")
    public ResponseEntity<ApiResponse<ArticleResponse>> depublierArticle(@PathVariable Long id) {
        ArticleResponse article = articleService.depublierArticle(id);
        return ResponseEntity.ok(ApiResponse.success(article, "Article retiré de la publication."));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Supprimer un article")
    public ResponseEntity<ApiResponse<Void>> deleteArticle(@PathVariable Long id) {
        articleService.deleteArticle(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Article supprimé avec succès."));
    }
}
