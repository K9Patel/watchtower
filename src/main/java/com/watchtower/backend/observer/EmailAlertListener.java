package com.watchtower.backend.observer;

import com.watchtower.backend.entity.Alert;
import com.watchtower.backend.service.EmailAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AJT — Observer Pattern: Concrete Observer #2.
 * Sends an HTML email via JavaMailSender for HIGH or CRITICAL alerts.
 * Registered automatically as an AlertListener bean by Spring.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailAlertListener implements AlertListener {

    private final EmailAlertService emailAlertService;

    @Override
    public void onAlert(Alert alert) {
        // Only email for HIGH or CRITICAL severity — avoid alert fatigue
        if (alert.getSeverity() == Alert.Severity.LOW ||
            alert.getSeverity() == Alert.Severity.MEDIUM) {
            return;
        }

        log.info("EmailAlertListener: sending {} alert email for device {}",
                alert.getSeverity(), alert.getDevice().getDeviceName());
        emailAlertService.sendAlertEmail(alert);
    }
}
