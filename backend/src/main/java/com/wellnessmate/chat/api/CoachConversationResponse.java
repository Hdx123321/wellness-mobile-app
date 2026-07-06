package com.wellnessmate.chat.api;

import java.time.Instant;

public record CoachConversationResponse(
    Long id, Long clientId, String clientName, Long coachId, String coachName,
    String subject, String lastMessage, Instant updatedAt) {}
