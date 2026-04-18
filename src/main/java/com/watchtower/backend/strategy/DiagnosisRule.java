package com.watchtower.backend.strategy;

import com.watchtower.backend.entity.Alert;
import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.UsageLog;

import java.util.List;
import java.util.Optional;

/**
 * AJT — Strategy Pattern: Strategy interface.
 * DiagnosisEngine holds a List<DiagnosisRule> and calls evaluate() on each
 * every 30 seconds. If a rule detects a problem it returns an Alert to save.
 */
public interface DiagnosisRule {

    /**
     * Evaluate recent logs for a single device.
     *
     * @param device      the device being evaluated
     * @param recentLogs  all UsageLogs for this device in the last 60 seconds
     * @return Optional<Alert> — present if this rule fired, empty if device is fine
     */
    Optional<Alert> evaluate(Device device, List<UsageLog> recentLogs);
}
