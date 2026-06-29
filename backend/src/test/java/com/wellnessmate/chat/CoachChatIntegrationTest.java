package com.wellnessmate.chat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wellnessmate.auth.domain.UserAccount;
import com.wellnessmate.auth.repository.UserAccountRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CoachChatIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserAccountRepository users;

  @Test
  void clientAndCoachCanExchangeOwnedMessages() throws Exception {
    String clientToken = register("chat-client", "chat-client@example.com");
    register("chat-coach", "chat-coach@example.com");
    UserAccount coach = users.findByUsernameIgnoreCase("chat-coach").orElseThrow();
    coach.promoteToCoach();
    users.flush();
    String coachToken = login("chat-coach");

    String conversationsJson = mockMvc.perform(get("/api/coach-chat/conversations")
            .header("Authorization", bearer(clientToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].coachName").value("chat-coach"))
        .andReturn().getResponse().getContentAsString();
    long conversationId = objectMapper.readTree(conversationsJson).get(0).path("id").asLong();

    mockMvc.perform(post("/api/coach-chat/conversations/{id}/messages", conversationId)
            .header("Authorization", bearer(clientToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"Can we review today's plan?\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.senderRole").value("CLIENT"));

    mockMvc.perform(get("/api/coach-chat/conversations")
            .header("Authorization", bearer(coachToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].clientName").value("chat-client"));

    mockMvc.perform(get("/api/coach-chat/conversations/{id}/messages", conversationId)
            .header("Authorization", bearer(coachToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].content").value("Can we review today's plan?"));
  }

  private String register(String username, String email) throws Exception {
    String response = mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "username", username, "email", email,
                "password", "StrongPass123", "displayName", username))))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(response).path("accessToken").asText();
  }

  private String login(String identifier) throws Exception {
    String response = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "identifier", identifier, "password", "StrongPass123"))))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(response).path("accessToken").asText();
  }

  private String bearer(String token) { return "Bearer " + token; }
}
