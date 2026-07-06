package com.wellnessmate.advisor.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameSessionRequest(@NotBlank @Size(max = 100) String title) {}
