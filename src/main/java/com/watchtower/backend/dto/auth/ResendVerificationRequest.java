package com.watchtower.backend.dto.auth;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ResendVerificationRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
}
