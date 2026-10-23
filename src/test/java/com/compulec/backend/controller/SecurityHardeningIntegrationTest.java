package com.compulec.backend.controller;

import com.compulec.backend.entity.Article;
import com.compulec.backend.entity.Role;
import com.compulec.backend.repository.ArticleRepository;
import com.compulec.backend.service.EmailService;
import com.compulec.backend.support.TestAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Brouillons invisibles du public, contrôle du contenu des fichiers envoyés, en-têtes de sécurité.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityHardeningIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private TestAccounts testAccounts;

    @MockitoBean
    private EmailService emailService;

    private Long draftId;
    private Long publishedId;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        articleRepository.deleteAll();
        testAccounts.deleteAllAccounts();
        testAccounts.createActiveUser("admin.securite@compulec.com", Role.ADMIN);
        adminToken = testAccounts.login(mockMvc, "admin.securite@compulec.com", TestAccounts.PASSWORD).accessToken();

        draftId = articleRepository.save(Article.builder().title("Brouillon confidentiel").published(false).build()).getId();
        publishedId = articleRepository.save(Article.builder().title("Article public").published(true).build()).getId();
    }

    @Test
    void draft_IsInvisibleToVisitors_ButVisibleToStaff() throws Exception {
        mockMvc.perform(get("/articles/" + draftId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/articles/" + publishedId)).andExpect(status().isOk());
        mockMvc.perform(get("/articles/" + draftId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Brouillon confidentiel"));
    }

    @Test
    void fullListIncludingDrafts_RequiresStaff() throws Exception {
        mockMvc.perform(get("/articles")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/projets")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/articles/publies")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));
        mockMvc.perform(get("/articles").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void upload_HtmlDisguisedAsPng_IsRejected() throws Exception {
        mockMvc.perform(multipart("/articles")
                        .file(new MockMultipartFile("title", "", "text/plain", "Test".getBytes()))
                        .file(new MockMultipartFile("imageFile", "photo.png", "image/png",
                                "<html><script>alert(document.cookie)</script></html>".getBytes()))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Format de fichier non autorisé : seules les images JPG, PNG, GIF et WEBP sont acceptées."));
    }

    @Test
    void upload_SvgIsRejected_RealPngIsAccepted() throws Exception {
        mockMvc.perform(multipart("/articles")
                        .file(new MockMultipartFile("title", "", "text/plain", "Test".getBytes()))
                        .file(new MockMultipartFile("imageFile", "logo.svg", "image/svg+xml", "<svg onload=alert(1)/>".getBytes()))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart("/articles")
                        .file(new MockMultipartFile("title", "", "text/plain", "Test".getBytes()))
                        .file(new MockMultipartFile("imageFile", "vraie.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.data.imageUrl", org.hamcrest.Matchers.endsWith(".png")));
    }

    @Test
    void responses_CarryNoSniffHeader() throws Exception {
        mockMvc.perform(get("/articles/publies"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void invalidSortParameter_Gives400Not500() throws Exception {
        mockMvc.perform(get("/articles?sort=inexistant").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }
}
