package com.compulec.backend.service;

import com.compulec.backend.dto.request.ArticleRequest;
import com.compulec.backend.dto.response.ArticleResponse;
import com.compulec.backend.entity.Article;
import com.compulec.backend.exception.ResourceNotFoundException;
import com.compulec.backend.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public ArticleResponse createArticle(ArticleRequest request, MultipartFile imageFile, String imageUrl) {
        String finalImageUrl = imageUrl;

        // Si un fichier est fourni, l'enregistrer et utiliser son URL
        if (imageFile != null && !imageFile.isEmpty()) {
            finalImageUrl = fileStorageService.storeFile(imageFile);
        }

        Article article = Article.builder()
                .title(request.getTitle())
                .subtitle(request.getSubtitle())
                .heading(request.getHeading())
                .overview(request.getOverview())
                .imageUrl(finalImageUrl)
                .published(request.getPublished() != null ? request.getPublished() : false)
                .build();

        article = articleRepository.save(article);
        log.info("Nouvel article créé : '{}' (ID: {})", article.getTitle(), article.getId());
        return mapToResponse(article);
    }

    @Transactional(readOnly = true)
    public Page<ArticleResponse> getAllArticles(Pageable pageable) {
        return articleRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<ArticleResponse> getArticlesPublies(Pageable pageable) {
        return articleRepository.findByPublishedTrue(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public ArticleResponse getArticleById(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article", "id", id));
        return mapToResponse(article);
    }

    @Transactional
    public ArticleResponse updateArticle(Long id, ArticleRequest request, MultipartFile imageFile, String imageUrl) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article", "id", id));

        String finalImageUrl = article.getImageUrl();

        if (imageFile != null && !imageFile.isEmpty()) {
            finalImageUrl = fileStorageService.storeFile(imageFile);
        } else if (imageUrl != null) {
            finalImageUrl = imageUrl;
        }

        article.setTitle(request.getTitle());
        article.setSubtitle(request.getSubtitle());
        article.setHeading(request.getHeading());
        article.setOverview(request.getOverview());
        article.setImageUrl(finalImageUrl);
        if (request.getPublished() != null) {
            article.setPublished(request.getPublished());
        }

        article = articleRepository.save(article);
        log.info("Article mis à jour : '{}' (ID: {})", article.getTitle(), article.getId());
        return mapToResponse(article);
    }

    @Transactional
    public ArticleResponse publierArticle(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article", "id", id));
        
        article.setPublished(true);
        article = articleRepository.save(article);
        log.info("Article publié : '{}' (ID: {})", article.getTitle(), article.getId());
        return mapToResponse(article);
    }

    @Transactional
    public ArticleResponse depublierArticle(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Article", "id", id));
        
        article.setPublished(false);
        article = articleRepository.save(article);
        log.info("Article dépublié : '{}' (ID: {})", article.getTitle(), article.getId());
        return mapToResponse(article);
    }

    @Transactional
    public void deleteArticle(Long id) {
        if (!articleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Article", "id", id);
        }
        articleRepository.deleteById(id);
        log.info("Article supprimé avec l'ID : {}", id);
    }

    private ArticleResponse mapToResponse(Article article) {
        return ArticleResponse.builder()
                .id(article.getId())
                .title(article.getTitle())
                .subtitle(article.getSubtitle())
                .heading(article.getHeading())
                .overview(article.getOverview())
                .imageUrl(article.getImageUrl())
                .published(article.isPublished())
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .build();
    }
}
