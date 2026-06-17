package net.hackyourfuture.tickettrackingsystem.controller;

import net.hackyourfuture.tickettrackingsystem.email.EmailSendResult;
import net.hackyourfuture.tickettrackingsystem.email.ResendAutomationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Sql(
        statements = {
                "TRUNCATE TABLE user_ticket, tickets, users, projects RESTART IDENTITY CASCADE",
                "INSERT INTO projects (name) VALUES ('Project Alpha')",
                "INSERT INTO users (name, email) VALUES ('Alice Doe', 'alice@example.com')",
                "INSERT INTO tickets (title, description, project_id, status) VALUES ('Bug login', 'Login button is broken', 1, 'OPEN')",
                "INSERT INTO tickets (title, description, project_id, status) VALUES ('Feature export', 'Export tickets to CSV', 1, 'CLOSED')",
                "INSERT INTO user_ticket (user_id, ticket_id) VALUES (1, 1)"
        },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class TicketControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResendAutomationService resendAutomationService;

    @BeforeEach
    void setUp() {
        when(resendAutomationService.sendTicketUpdatedEmail(
                anyString(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        )).thenAnswer(invocation ->
                new EmailSendResult(
                        invocation.getArgument(0),
                        true,
                        "Email sent successfully"
                )
        );
    }

    @Test
    void getAllTickets_withoutFilters_returnsAllTickets() throws Exception {
        mockMvc.perform(get("/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("Bug login"))
                .andExpect(jsonPath("$[1].title").value("Feature export"));
    }

    @Test
    void getAllTickets_withTextAndStatus_usesAndLogic() throws Exception {
        mockMvc.perform(get("/tickets")
                        .param("text", "bug")
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Bug login"))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    void updateTicket_returnsTicketAndEmailNotificationResult() throws Exception {
        String requestBody = """
                {
                  "title": "Bug login updated",
                  "description": "Login button is still broken",
                  "projectId": 1,
                  "status": "IN_PROGRESS"
                }
                """;

        mockMvc.perform(put("/tickets/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.id").value(1))
                .andExpect(jsonPath("$.ticket.title").value("Bug login updated"))
                .andExpect(jsonPath("$.ticket.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.emailNotifications", hasSize(1)))
                .andExpect(jsonPath("$.emailNotifications[0].recipientEmail").value("alice@example.com"))
                .andExpect(jsonPath("$.emailNotifications[0].sent").value(true))
                .andExpect(jsonPath("$.emailNotifications[0].message").value("Email sent successfully"));
    }
}