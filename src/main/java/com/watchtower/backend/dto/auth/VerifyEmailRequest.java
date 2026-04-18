package com.watchtower.backend.dto.auth;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class VerifyEmailRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Verification code is required")
    @Size(min = 6, max = 6, message = "Code must be exactly 6 digits")
    private String code;
}
