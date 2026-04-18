package com.watchtower.backend.strategy;

import com.watchtower.backend.entity.Alert;
import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.UsageLog;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * AJT — Strategy Pattern: Rule 1 — High Bandwidth
 * Fires when average bandwidth exceeds 80% over the last 60 seconds.
 * Severity: HIGH
 */
@Component
public class HighBandwidthRule implements DiagnosisRule {

    private static final double THRESHOLD = 80.0;

    @Override
    public Optional<Alert> evaluate(Device device, List<UsageLog> recentLogs) {
        if (recentLogs.isEmpty()) return Optional.empty();

        double avgBandwidth = recentLogs.stream()
                .mapToDouble(UsageLog::getBandwidthPercentage)
                .average()
                .orElse(0.0);

        if (avgBandwidth >= THRESHOLD) {
            double rounded = Math.round(avgBandwidth * 10.0) / 10.0;
            return Optional.of(Alert.builder()
                    .device(device)
                    .alertType("CONGESTION")
                    .severity(Alert.Severity.HIGH)
                    .message(String.format(
                            "Device %s is using %.1f%% average bandwidth — congestion detected.",
                            device.getDeviceName(), rounded))
                    .build());
        }

        return Optional.empty();
    }
}
