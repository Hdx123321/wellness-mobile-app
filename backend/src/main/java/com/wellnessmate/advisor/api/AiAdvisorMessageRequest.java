package com.wellnessmate.advisor.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiAdvisorMessageRequest(@NotBlank @Size(max = 2000) String content) {}
