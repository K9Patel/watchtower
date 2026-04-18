package com.watchtower.backend.strategy;

import com.watchtower.backend.entity.Alert;
import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.UsageLog;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * AJT — Strategy Pattern: Rule 2 — Traffic Spike
 * Fires when ANY single reading exceeds 200 MB in the last 60 seconds.
 * Indicates a sudden large download or upload burst.
 * Severity: MEDIUM → HIGH depending on magnitude
 */
@Component
public class TrafficSpikeRule implements DiagnosisRule {

    private static final double SPIKE_MB = 200.0;

    @Override
    public Optional<Alert> evaluate(Device device, List<UsageLog> recentLogs) {
        Optional<UsageLog> spike = recentLogs.stream()
                .filter(log -> log.getBytesUsed() >= SPIKE_MB)
                .max((a, b) -> Double.compare(a.getBytesUsed(), b.getBytesUsed()));

        if (spike.isPresent()) {
            double maxMB = spike.get().getBytesUsed();
            Alert.Severity severity = maxMB >= 400 ? Alert.Severity.HIGH : Alert.Severity.MEDIUM;

            return Optional.of(Alert.builder()
                    .device(device)
                    .alertType("SPIKE")
                    .severity(severity)
                    .message(String.format(
                            "Device %s recorded a traffic spike of %.1f MB in a single reading.",
                            device.getDeviceName(), maxMB))
                    .build());
        }

        return Optional.empty();
    }
}
