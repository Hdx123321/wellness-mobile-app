package com.wellnessmate.chat.api;

import java.time.Instant;

public record CoachConversationResponse(
    Long id, String clientName, String coachName, String lastMessage, Instant updatedAt) {}
