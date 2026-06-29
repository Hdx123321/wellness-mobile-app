package com.wellnessmate.chat.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CoachMessageRequest(@NotBlank @Size(max = 2000) String content) {}
