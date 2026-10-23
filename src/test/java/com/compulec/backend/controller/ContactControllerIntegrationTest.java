package com.compulec.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.compulec.backend.dto.request.ContactRequest;
import com.compulec.backend.entity.Contact;
import com.compulec.backend.entity.Role;
import com.compulec.backend.repository.ContactRepository;
import com.compulec.backend.service.EmailService;
import com.compulec.backend.support.TestAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;


import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ContactControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContactRepository contactRepository;

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
        contactRepository.deleteAll();
        testAccounts.deleteAllAccounts();

        // Admin actif avec 2FA configurée, puis connexion complète (mot de passe + code 2FA)
        testAccounts.createActiveUser("admin.contact@compulec.com", Role.ADMIN);
        adminToken = testAccounts.login(mockMvc, "admin.contact@compulec.com", TestAccounts.PASSWORD).accessToken();
    }

    // ===================================================================
    // TEST 1 : Créer un contact (accès public, sans authentification)
    // ===================================================================
    @Test
    void testCreateContact_PublicAccess_Success() throws Exception {
        ContactRequest request = ContactRequest.builder()
                .nom("Client Compulec")
                .email("client@compulec.com")
                .objet("Demande de devis électricité")
                .message("Bonjour, je souhaite un devis pour l'installation solaire.")
                .build();

        mockMvc.perform(post("/api/contacts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nom").value("Client Compulec"))
                .andExpect(jsonPath("$.data.lu").value(false));

        assertEquals(1, contactRepository.count());
    }

    // ===================================================================
    // TEST 2 : Lister les contacts sans authentification → 401
    // ===================================================================
    @Test
    void testGetAllContacts_Unauthenticated_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/contacts")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ===================================================================
    // TEST 3 : Lister les contacts avec token Admin → 200
    // ===================================================================
    @Test
    void testGetAllContacts_AsAdmin_Success() throws Exception {
        Contact contact = Contact.builder()
                .nom("Client Test")
                .email("test@client.com")
                .objet("Sujet")
                .message("Message de test long de dix caractères")
                .lu(false)
                .build();
        contactRepository.save(contact);

        mockMvc.perform(get("/api/contacts")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].nom").value("Client Test"));
    }

    // ===================================================================
    // TEST 4 : Marquer un contact comme lu → 200
    // ===================================================================
    @Test
    void testMarkAsRead_Success() throws Exception {
        Contact contact = Contact.builder()
                .nom("Client Test")
                .email("test@client.com")
                .objet("Sujet")
                .message("Message de test long de dix caractères")
                .lu(false)
                .build();
        Contact saved = contactRepository.save(contact);

        mockMvc.perform(patch("/api/contacts/" + saved.getId() + "/lire")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lu").value(true));

        assertTrue(contactRepository.findById(saved.getId()).orElseThrow().isLu());
    }

    // ===================================================================
    // TEST 5 : Supprimer un contact avec token Admin → 200
    // ===================================================================
    @Test
    void testDeleteContact_AsAdmin_Success() throws Exception {
        Contact contact = Contact.builder()
                .nom("Client Test")
                .email("test@client.com")
                .objet("Sujet")
                .message("Message de test long de dix caractères")
                .lu(false)
                .build();
        Contact saved = contactRepository.save(contact);

        mockMvc.perform(delete("/api/contacts/" + saved.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        assertEquals(0, contactRepository.count());
    }
}
