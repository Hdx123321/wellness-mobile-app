package com.wellnessmate.chat.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
    @NotNull Long clientId,
    @Size(max = 120) String subject) {}
