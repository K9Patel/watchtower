package com.watchtower.backend.service;

import com.watchtower.backend.entity.Alert;
import com.watchtower.backend.entity.Device;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * AJT Unit 7 — JavaMailSender:
 * Sends an HTML alert email to the admin when HIGH or CRITICAL alerts fire.
 * Called by EmailAlertListener (Observer) on Day 8 integration.
 */
@Slf4j
@Service
public class EmailAlertService {

    private final JavaMailSender mailSender;
    private boolean emailEnabled = true;

    @Value("${watchtower.admin.email}")
    private String adminEmail;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    public EmailAlertService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @PostConstruct
    void init() {
        if (mailPassword == null || mailPassword.isBlank()
                || mailPassword.equalsIgnoreCase("YOUR_APP_PASSWORD")) {
            emailEnabled = false;
            log.warn("EmailAlertService: SMTP password not configured — email alerts DISABLED. "
                    + "Set 'spring.mail.password' to a valid Gmail App Password to enable.");
        } else {
            log.info("EmailAlertService: email alerts ENABLED for {}", adminEmail);
        }
    }

    /**
     * Send HTML email for a given Alert.
     * Only called for HIGH or CRITICAL severity by EmailAlertListener.
     */
    public void sendAlertEmail(Alert alert) {
        if (!emailEnabled) {
            log.debug("EmailAlertService: email disabled, skipping alert for {}",
                    alert.getDevice().getDeviceName());
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(adminEmail);
            helper.setSubject("[WatchTower] " + alert.getSeverity() + " Alert — "
                    + alert.getDevice().getDeviceName());
            helper.setText(buildHtmlBody(alert), true);

            mailSender.send(message);
            log.info("EmailAlertService: sent {} alert email to {}", alert.getSeverity(), adminEmail);

        } catch (MessagingException | MailException e) {
            log.error("EmailAlertService: failed to send email — {}", e.getMessage());
        }
    }

    private String buildHtmlBody(Alert alert) {
        Device device = alert.getDevice();
        String severityColor = switch (alert.getSeverity()) {
            case CRITICAL -> "#dc2626";  // red
            case HIGH     -> "#ea580c";  // orange
            case MEDIUM   -> "#ca8a04";  // yellow
            case LOW      -> "#16a34a";  // green
        };

        return """
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif; background: #0f172a; color: #e2e8f0; padding: 32px;">
              <div style="max-width: 600px; margin: auto; background: #1e293b;
                          border-radius: 12px; padding: 32px; border: 1px solid #334155;">
                <h1 style="color: %s; margin-top: 0;">⚠ WatchTower Alert</h1>
                <table style="width: 100%%; border-collapse: collapse;">
                  <tr><td style="padding: 8px; color: #94a3b8;">Severity</td>
                      <td style="padding: 8px; color: %s; font-weight: bold;">%s</td></tr>
                  <tr><td style="padding: 8px; color: #94a3b8;">Alert Type</td>
                      <td style="padding: 8px;">%s</td></tr>
                  <tr><td style="padding: 8px; color: #94a3b8;">Device</td>
                      <td style="padding: 8px;">%s</td></tr>
                  <tr><td style="padding: 8px; color: #94a3b8;">IP Address</td>
                      <td style="padding: 8px;">%s</td></tr>
                  <tr><td style="padding: 8px; color: #94a3b8;">Message</td>
                      <td style="padding: 8px;">%s</td></tr>
                  <tr><td style="padding: 8px; color: #94a3b8;">Time</td>
                      <td style="padding: 8px;">%s</td></tr>
                </table>
                <p style="margin-top: 24px; color: #64748b; font-size: 12px;">
                  WatchTower Network Intelligence System — automated alert
                </p>
              </div>
            </body>
            </html>
            """.formatted(
                severityColor, severityColor, alert.getSeverity(),
                alert.getAlertType(),
                device.getDeviceName(),
                device.getIpAddress(),
                alert.getMessage(),
                alert.getCreatedAt()
        );
    }
}
