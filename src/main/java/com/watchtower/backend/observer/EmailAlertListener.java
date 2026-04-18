package com.watchtower.backend.observer;

import com.watchtower.backend.entity.Alert;
import com.watchtower.backend.service.EmailAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AJT — Observer Pattern: Concrete Observer #2.
 * Sends an HTML email via JavaMailSender for HIGH or CRITICAL alerts.
 * Registered automatically as an AlertListener bean by Spring.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailAlertListener implements AlertListener {

    private static final long COOLDOWN_MS = 5 * 60 * 1000; // 5 minutes

    private final EmailAlertService emailAlertService;
    private final Map<String, Long> lastEmailByAlertKey = new ConcurrentHashMap<>();

    @Override
    public void onAlert(Alert alert) {
        // Product policy: send email only for CRITICAL alerts.
        if (alert.getSeverity() != Alert.Severity.CRITICAL) {
            return;
        }

        String key = alert.getDevice().getId() + ":" + alert.getAlertType();
        long now = System.currentTimeMillis();
        long lastSent = lastEmailByAlertKey.getOrDefault(key, 0L);
        if ((now - lastSent) < COOLDOWN_MS) {
            log.debug("EmailAlertListener: cooldown active for key {} — skipping", key);
            return;
        }
        lastEmailByAlertKey.put(key, now);

        log.info("EmailAlertListener: sending {} alert email for device {}",
                alert.getSeverity(), alert.getDevice().getDeviceName());
        emailAlertService.sendAlertEmail(alert);
    }
}
