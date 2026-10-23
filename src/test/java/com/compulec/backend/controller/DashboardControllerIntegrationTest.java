package com.compulec.backend.controller;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.YearMonth;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DashboardControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private TestAccounts testAccounts;

    @MockitoBean
    private EmailService emailService;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        contactRepository.deleteAll();
        testAccounts.deleteAllAccounts();

        testAccounts.createActiveUser("admin.dashboard@compulec.com", Role.ADMIN);
        adminToken = testAccounts.login(mockMvc, "admin.dashboard@compulec.com", TestAccounts.PASSWORD).accessToken();

        contactRepository.save(contact("Alice", false));
        contactRepository.save(contact("Bruno", true));
    }

    @Test
    void stats_CountsInDatabase() throws Exception {
        mockMvc.perform(get("/dashboard/stats").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalMessages").value(2))
                .andExpect(jsonPath("$.data.unreadMessages").value(1))
                .andExpect(jsonPath("$.data.adminCount").value(1))
                .andExpect(jsonPath("$.data.activeStaff").value(1))
                .andExpect(jsonPath("$.data.activeChatSessions").value(0));
    }

    @Test
    void interactionsChart_SixIsoMonths_CurrentMonthCounted() throws Exception {
        String currentMonth = YearMonth.now().toString();
        mockMvc.perform(get("/dashboard/interactions-chart").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[5].month").value(currentMonth))
                .andExpect(jsonPath("$.data[5].messages").value(2));
    }

    @Test
    void recentMessages_InvalidLimitIsClampedInsteadOfFailing() throws Exception {
        mockMvc.perform(get("/dashboard/recent-messages?limit=0").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void recentActivity_ReturnsStructuredItems() throws Exception {
        mockMvc.perform(get("/dashboard/recent-activity?limit=10").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.type == 'MESSAGE')].action", hasItem("RECEIVED")))
                .andExpect(jsonPath("$.data[?(@.type == 'MESSAGE')].subject", hasItem("Alice")));
    }

    @Test
    void notifications_StructuredWithIsReadFlag_AndMarkAllAsRead() throws Exception {
        mockMvc.perform(get("/notifications").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("CONTACT"))
                .andExpect(jsonPath("$.data[0].count").value(1))
                .andExpect(jsonPath("$.data[0].isRead").value(false))
                .andExpect(jsonPath("$.data[0].date").exists());

        mockMvc.perform(post("/notifications/mark-all-read").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/dashboard/stats").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.data.unreadMessages").value(0));
    }

    @Test
    void dashboard_RequiresAuthentication() throws Exception {
        mockMvc.perform(get("/dashboard/stats"))
                .andExpect(status().isUnauthorized());
    }

    private static Contact contact(String nom, boolean lu) {
        return Contact.builder()
                .nom(nom)
                .email(nom.toLowerCase() + "@example.com")
                .objet("Demande de devis")
                .message("Bonjour")
                .lu(lu)
                .build();
    }
}
