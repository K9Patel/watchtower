package com.watchtower.backend.observer;

import com.watchtower.backend.entity.Alert;
import com.watchtower.backend.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AJT — Observer Pattern: Concrete Observer #1.
 * Persists every Alert to the database when DiagnosisEngine fires a rule.
 * Registered automatically by Spring as an AlertListener bean.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseAlertListener implements AlertListener {

    private final AlertRepository alertRepository;

    @Override
    public void onAlert(Alert alert) {
        // Check for duplicate unresolved alert on the same device of same type
        boolean alreadyOpen = alertRepository
                .findByDeviceAndIsResolvedFalse(alert.getDevice())
                .stream()
                .anyMatch(a -> a.getAlertType().equals(alert.getAlertType()));

        if (alreadyOpen) {
            log.debug("DatabaseAlertListener: duplicate {} alert for {} — skipping",
                    alert.getAlertType(), alert.getDevice().getDeviceName());
            return;
        }

        Alert saved = alertRepository.save(alert);
        log.info("DatabaseAlertListener: saved alert #{} — {} [{}] for {}",
                saved.getId(),
                saved.getAlertType(),
                saved.getSeverity(),
                saved.getDevice().getDeviceName());
    }
}
