package com.watchtower.backend.controller;

import com.watchtower.backend.dto.auth.*;
import com.watchtower.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication REST controller — /api/v1/auth/*
 *
 * Public endpoints: signup, verify-email, resend-verification, login, forgot-password, reset-password
 * Protected endpoints: logout, user (require valid JWT)
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // ── PUBLIC ──────────────────────────────────────────────

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        AuthResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<AuthResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        AuthResponse response = authService.verifyEmail(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<AuthResponse> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        AuthResponse response = authService.resendVerification(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<AuthResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        AuthResponse response = authService.forgotPassword(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<AuthResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        AuthResponse response = authService.resetPassword(request);
        return ResponseEntity.ok(response);
    }

    // ── PROTECTED ───────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        // JWT is stateless — client should discard the token
        return ResponseEntity.ok(Map.of("message", "Logged out successfully."));
    }

    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getUser(Authentication authentication) {
        String email = authentication.getName();
        UserDto user = authService.getCurrentUser(email);
        return ResponseEntity.ok(Map.of("user", user));
    }
}
