package com.wellnessmate.advisor.api;

import com.wellnessmate.advisor.domain.AiChatSession;
import java.time.Instant;

public record AiChatSessionResponse(Long id, String title, Instant createdAt, Instant updatedAt) {
  public static AiChatSessionResponse from(AiChatSession session) {
    return new AiChatSessionResponse(
        session.getId(), session.getTitle(), session.getCreatedAt(), session.getUpdatedAt());
  }
}
