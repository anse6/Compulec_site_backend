package com.compulec.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.compulec.backend.entity.Projet;
import com.compulec.backend.entity.Role;
import com.compulec.backend.repository.ProjetRepository;
import com.compulec.backend.service.EmailService;
import com.compulec.backend.support.TestAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ProjetControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjetRepository projetRepository;

    @Autowired
    private TestAccounts testAccounts;

    // ObjectMapper avec support des dates Java (JavaTimeModule)
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // Mock EmailService pour éviter toute connexion SMTP pendant les tests
    @MockitoBean
    private EmailService emailService;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        projetRepository.deleteAll();
        testAccounts.deleteAllAccounts();

        // Admin actif avec 2FA configurée, puis connexion complète (mot de passe + code 2FA)
        testAccounts.createActiveUser("admin.projet@compulec.com", Role.ADMIN);
        adminToken = testAccounts.login(mockMvc, "admin.projet@compulec.com", TestAccounts.PASSWORD).accessToken();
    }

    // ===================================================================
    // Helpers
    // ===================================================================

    private static MockMultipartFile part(String name, String value) {
        return new MockMultipartFile(name, "", "text/plain", value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Requête de création complète, telle que l'envoie le formulaire (listes + carte). */
    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder projetRequest(
            String latitude, String longitude) {
        var request = multipart("/projets")
                .file(part("titre", "Vidéosurveillance du siège"))
                .file(part("categorie", "Computer Security"))
                .file(part("service", "Video Surveillance, Biometrics & Access Control"))
                .file(part("secteur", "Banking, Finance & Insurance"))
                .file(part("localisation", "Boulevard de la Liberté, Akwa, Douala, Littoral, Cameroun"))
                .file(part("overview", "Vue d'ensemble"))
                .file(part("contexte", "Contexte"))
                .file(part("solution", "Solution"))
                .file(part("resultat", "Résultat"));
        if (latitude != null) {
            request.file(part("latitude", latitude)).file(part("longitude", longitude));
        }
        return request;
    }

    private Projet savedProjet(String titre, boolean publie) {
        return projetRepository.save(Projet.builder()
                .titre(titre)
                .categorie("Energy Solutions")
                .secteur("Energy & Utilities")
                .localisation("Douala")
                .publie(publie)
                .images(new ArrayList<>())
                .build());
    }

    // ===================================================================
    // TEST 1 : Créer un projet (services multiples + position GPS) → 201
    // ===================================================================
    @Test
    void testCreateProjet_WithServicesAndMapLocation_Success() throws Exception {
        mockMvc.perform(projetRequest("4.051234", "9.700456")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.categorie").value("Computer Security"))
                .andExpect(jsonPath("$.data.service").value("Video Surveillance, Biometrics & Access Control"))
                .andExpect(jsonPath("$.data.secteur").value("Banking, Finance & Insurance"))
                .andExpect(jsonPath("$.data.latitude").value(4.051234))
                .andExpect(jsonPath("$.data.longitude").value(9.700456))
                .andExpect(jsonPath("$.data.publie").value(false));

        assertEquals(1, projetRepository.count());
    }

    // ===================================================================
    // TEST 2 : Coordonnées GPS invalides → 400
    // ===================================================================
    @Test
    void testCreateProjet_InvalidCoordinates_BadRequest() throws Exception {
        mockMvc.perform(projetRequest("95.5", "9.7")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Coordonnée GPS hors limites : 95.5"));

        mockMvc.perform(projetRequest("abc", "9.7")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());

        assertEquals(0, projetRepository.count());
    }

    // ===================================================================
    // TEST 3 : Lister les projets publiés (accès public, sans authentification)
    // ===================================================================
    @Test
    void testGetProjetsPublies_PublicAccess() throws Exception {
        savedProjet("Projet Publié", true);
        savedProjet("Projet Non Publié", false);

        // L'accès public ne doit retourner que le projet publié
        mockMvc.perform(get("/projets/publies")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].titre").value("Projet Publié"));
    }

    // ===================================================================
    // TEST 4 : Publier un projet avec token Admin → 200
    // ===================================================================
    @Test
    void testPublierProjet_Success() throws Exception {
        Projet saved = savedProjet("Projet à publier", false);

        mockMvc.perform(patch("/projets/" + saved.getId() + "/publier")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publie").value(true));

        assertTrue(projetRepository.findById(saved.getId()).orElseThrow().isPublie());
    }

    // ===================================================================
    // TEST 5 : Créer un projet sans authentification → 401
    // ===================================================================
    @Test
    void testCreateProjet_Unauthenticated_Unauthorized() throws Exception {
        mockMvc.perform(projetRequest("4.05", "9.7"))
                .andExpect(status().isUnauthorized());
        assertEquals(0, projetRepository.count());
    }
}
