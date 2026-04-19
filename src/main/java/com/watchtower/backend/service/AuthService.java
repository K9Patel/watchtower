package com.watchtower.backend.service;

import com.watchtower.backend.dto.auth.*;
import com.watchtower.backend.entity.PasswordResetToken;
import com.watchtower.backend.entity.User;
import com.watchtower.backend.repository.PasswordResetTokenRepository;
import com.watchtower.backend.repository.UserRepository;
import com.watchtower.backend.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Core authentication service — signup, verify, login, forgot/reset password.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int VERIFICATION_TTL_MINUTES = 10;
    private static final int RESET_TTL_MINUTES = 10;
    private static final int CODE_LENGTH = 6;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final UserDataScopeService userDataScopeService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       PasswordResetTokenRepository resetTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       EmailService emailService,
                       UserDataScopeService userDataScopeService) {
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.userDataScopeService = userDataScopeService;
    }

    // ── SIGNUP ──────────────────────────────────────────────

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        // Validate password confirmation
        if (!request.getPassword().equals(request.getPasswordConfirmation())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Passwords do not match");
        }

        // Check if verified user already exists
        var existingUser = userRepository.findByUserEmail(email);
        if (existingUser.isPresent() && existingUser.get().getEmailVerifiedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered and verified");
        }

        // Generate verification code
        String code = generateCode();
        String hashedCode = passwordEncoder.encode(code);

        User user;
        if (existingUser.isPresent()) {
            // Update existing unverified user
            user = existingUser.get();
            user.setUserName(request.getName());
            user.setUserPassword(passwordEncoder.encode(request.getPassword()));
            user.setVerificationToken(hashedCode);
            user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(VERIFICATION_TTL_MINUTES));
        } else {
            // Create new user
            user = User.builder()
                    .userId(generateUlid())
                    .userName(request.getName())
                    .userEmail(email)
                    .userPassword(passwordEncoder.encode(request.getPassword()))
                    .verificationToken(hashedCode)
                    .verificationCodeExpiresAt(LocalDateTime.now().plusMinutes(VERIFICATION_TTL_MINUTES))
                    .build();
        }

        userRepository.save(user);

        // Send verification email
        emailService.sendVerificationCode(email, user.getUserName(), code);

        return AuthResponse.builder()
                .message("Verification code sent to your email.")
                .email(email)
                .build();
    }

    // ── VERIFY EMAIL ────────────────────────────────────────

    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        User user = findUserByEmail(email);

        if (user.getEmailVerifiedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already verified");
        }

        if (user.getVerificationCodeExpiresAt() == null ||
            user.getVerificationCodeExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Verification code has expired. Please request a new one.");
        }

        if (!passwordEncoder.matches(request.getCode(), user.getVerificationToken())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid verification code");
        }

        // Mark as verified
        user.setEmailVerifiedAt(LocalDateTime.now());
        user.setVerificationToken(null);
        user.setVerificationCodeExpiresAt(null);
        userRepository.save(user);

        // Generate JWT token
        String token = jwtService.generateToken(user, false);
        userDataScopeService.activateScope(user.getUserEmail());

        return AuthResponse.builder()
                .message("Email verified successfully.")
                .token(token)
                .user(UserDto.fromEntity(user))
                .build();
    }

    // ── RESEND VERIFICATION ─────────────────────────────────

    @Transactional
    public AuthResponse resendVerification(ResendVerificationRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        User user = findUserByEmail(email);

        if (user.getEmailVerifiedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already verified");
        }

        String code = generateCode();
        user.setVerificationToken(passwordEncoder.encode(code));
        user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(VERIFICATION_TTL_MINUTES));
        userRepository.save(user);

        emailService.sendVerificationCode(email, user.getUserName(), code);

        return AuthResponse.builder()
                .message("Verification code resent to your email.")
                .email(email)
                .build();
    }

    // ── LOGIN ───────────────────────────────────────────────

    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        User user = findUserByEmail(email);

        if (!passwordEncoder.matches(request.getPassword(), user.getUserPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        if (user.getEmailVerifiedAt() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Please verify your email before logging in");
        }

        String token = jwtService.generateToken(user, request.isRemember());
        var expiresAt = jwtService.getExpirationForToken(request.isRemember());
        userDataScopeService.activateScope(user.getUserEmail());

        return AuthResponse.builder()
                .message("Login successful.")
                .token(token)
                .expiresAt(expiresAt.toInstant().toString())
                .user(UserDto.fromEntity(user))
                .build();
    }

    // ── FORGOT PASSWORD ─────────────────────────────────────

    @Transactional
    public AuthResponse forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        // Always return success for security (don't leak user existence)
        var userOpt = userRepository.findByUserEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String code = generateCode();

            // Upsert reset token
            PasswordResetToken resetToken = resetTokenRepository.findByEmail(email)
                    .orElse(new PasswordResetToken());
            resetToken.setEmail(email);
            resetToken.setToken(passwordEncoder.encode(code));
            resetToken.setCreatedAt(LocalDateTime.now());
            resetTokenRepository.save(resetToken);

            emailService.sendPasswordResetCode(email, user.getUserName(), code);
        }

        return AuthResponse.builder()
                .message("If your email exists, you will receive a reset code.")
                .build();
    }

    // ── RESET PASSWORD ──────────────────────────────────────

    @Transactional
    public AuthResponse resetPassword(ResetPasswordRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        if (!request.getPassword().equals(request.getPasswordConfirmation())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Passwords do not match");
        }

        PasswordResetToken resetToken = resetTokenRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No reset code found. Please request a new one."));

        // Check expiry
        if (resetToken.getCreatedAt().plusMinutes(RESET_TTL_MINUTES).isBefore(LocalDateTime.now())) {
            resetTokenRepository.delete(resetToken);
            throw new ResponseStatusException(HttpStatus.GONE, "Reset code has expired. Please request a new one.");
        }

        // Validate code
        if (!passwordEncoder.matches(request.getCode(), resetToken.getToken())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid reset code");
        }

        // Update password
        User user = findUserByEmail(email);
        user.setUserPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        // Clean up
        resetTokenRepository.delete(resetToken);

        return AuthResponse.builder()
                .message("Password reset successfully. You can now login.")
                .build();
    }

    // ── GET CURRENT USER ────────────────────────────────────

    public UserDto getCurrentUser(String email) {
        User user = findUserByEmail(email);
        return UserDto.fromEntity(user);
    }

    // ── HELPERS ─────────────────────────────────────────────

    private User findUserByEmail(String email) {
        return userRepository.findByUserEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private String generateCode() {
        int code = secureRandom.nextInt(999999);
        return String.format("%06d", code);
    }

    private String generateUlid() {
        // Simple ULID-like ID: timestamp prefix + random suffix (26 chars)
        long timestamp = System.currentTimeMillis();
        String timePart = Long.toString(timestamp, 36).toUpperCase();
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 26 - timePart.length()).toUpperCase();
        return (timePart + randomPart).substring(0, 26);
    }
}
