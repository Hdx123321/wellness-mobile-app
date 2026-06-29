package com.wellnessmate.advisor.api;

import com.wellnessmate.advisor.domain.AiChatMessage;
import java.time.Instant;

public record AiAdvisorMessageResponse(Long id, String role, String content, Instant createdAt) {
  public static AiAdvisorMessageResponse from(AiChatMessage message) {
    return new AiAdvisorMessageResponse(message.getId(), message.getRole(), message.getContent(),
        message.getCreatedAt());
  }
}
