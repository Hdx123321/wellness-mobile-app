package com.wellnessmate.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Username/email login input. @author TODO(team member) */
public record LoginRequest(
    @NotBlank @Size(max = 255) String identifier,
    @NotBlank @Size(max = 72) String password
) {
}
