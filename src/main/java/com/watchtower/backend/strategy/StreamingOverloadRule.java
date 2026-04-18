package com.watchtower.backend.strategy;

import com.watchtower.backend.entity.Alert;
import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.UsageLog;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * AJT — Strategy Pattern: Rule 3 — Streaming Overload
 * Fires when a device has multiple STREAMING logs in the last 60 seconds
 * AND average bandwidth exceeds 50%.
 * Targets students streaming video during lecture hours.
 * Severity: MEDIUM
 */
@Component
public class StreamingOverloadRule implements DiagnosisRule {

    private static final int STREAMING_COUNT_THRESHOLD = 3;
    private static final double BANDWIDTH_THRESHOLD     = 50.0;

    @Override
    public Optional<Alert> evaluate(Device device, List<UsageLog> recentLogs) {
        long streamingCount = recentLogs.stream()
                .filter(log -> "STREAMING".equals(log.getTrafficType()))
                .count();

        double avgBandwidth = recentLogs.stream()
                .mapToDouble(UsageLog::getBandwidthPercentage)
                .average()
                .orElse(0.0);

        if (streamingCount >= STREAMING_COUNT_THRESHOLD && avgBandwidth >= BANDWIDTH_THRESHOLD) {
            return Optional.of(Alert.builder()
                    .device(device)
                    .alertType("STREAMING_OVERLOAD")
                    .severity(Alert.Severity.MEDIUM)
                    .message(String.format(
                            "Device %s has %d streaming sessions detected — %.1f%% avg bandwidth.",
                            device.getDeviceName(), streamingCount, avgBandwidth))
                    .build());
        }

        return Optional.empty();
    }
}
