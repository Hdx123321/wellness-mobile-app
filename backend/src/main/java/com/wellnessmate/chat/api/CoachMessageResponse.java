package com.wellnessmate.chat.api;

import java.time.Instant;

public record CoachMessageResponse(
    Long id, Long senderId, String senderName, String senderRole, String content, Instant createdAt) {}
