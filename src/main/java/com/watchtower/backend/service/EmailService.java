package com.watchtower.backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Email service for sending verification and password reset codes.
 * Falls back to console logging if SMTP is not configured.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    /**
     * Send a verification code email.
     */
    public void sendVerificationCode(String toEmail, String name, String code) {
        String subject = "Verify Your Email — WatchTower";
        sendCodeEmail(toEmail, name, code, subject, "verification");
    }

    /**
     * Send a password reset code email.
     */
    public void sendPasswordResetCode(String toEmail, String name, String code) {
        String subject = "Reset Your Password — WatchTower";
        sendCodeEmail(toEmail, name, code, subject, "password_reset");
    }

    private void sendCodeEmail(String toEmail, String name, String code, String subject, String type) {
        // Always log the code (for development/testing)
        log.info("═══════════════════════════════════════════════════");
        log.info("  {} CODE for {}: {}", type.toUpperCase(), toEmail, code);
        log.info("═══════════════════════════════════════════════════");

        // Skip actual email if SMTP not configured
        if (mailPassword == null || mailPassword.isBlank()) {
            log.warn("Mail password not configured — code logged to console only.");
            return;
        }

        try {
            Context ctx = new Context();
            ctx.setVariable("name", name);
            ctx.setVariable("code", code);
            ctx.setVariable("type", type);

            String htmlBody = templateEngine.process("emails/verification_code", ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("Email sent successfully to {}", toEmail);
        } catch (MessagingException | MailException e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
            // Don't throw — code is already logged to console
        }
    }
}
