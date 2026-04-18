package com.watchtower.backend.strategy;

import com.watchtower.backend.entity.Alert;
import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.UsageLog;
import com.watchtower.backend.repository.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * AJT — Strategy Pattern: Rule 2 — Traffic Spike
 * Fires when current 30-second bandwidth is > 3x the device's 5-minute baseline.
 * Severity: MEDIUM
 */
@Component
@RequiredArgsConstructor
public class TrafficSpikeRule implements DiagnosisRule {

    private static final double SPIKE_FACTOR = 3.0;

    private final UsageLogRepository usageLogRepository;

    @Override
    public Optional<Alert> evaluate(Device device, List<UsageLog> recentLogs) {
        if (recentLogs.isEmpty()) return Optional.empty();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fiveMinutesAgo = now.minusMinutes(5);
        LocalDateTime recentStart = now.minusSeconds(30);

        List<UsageLog> baselineWindow = usageLogRepository.findByDeviceAndTimestampAfter(device, fiveMinutesAgo)
            .stream()
            .filter(log -> log.getTimestamp().isBefore(recentStart))
            .toList();

        if (baselineWindow.isEmpty()) return Optional.empty();

        double currentAvg = recentLogs.stream()
            .mapToDouble(UsageLog::getBandwidthPercentage)
            .average()
            .orElse(0.0);

        double baselineAvg = baselineWindow.stream()
            .mapToDouble(UsageLog::getBandwidthPercentage)
            .average()
            .orElse(0.0);

        if (baselineAvg <= 0.0) return Optional.empty();

        if (currentAvg > (baselineAvg * SPIKE_FACTOR)) {
            return Optional.of(Alert.builder()
                .device(device)
                .alertType("SPIKE")
                .severity(Alert.Severity.MEDIUM)
                .message(String.format(
                    "%s traffic spike detected (%.1f%% now vs %.1f%% baseline).",
                    device.getDeviceName(), currentAvg, baselineAvg))
                .build());
        }

        return Optional.empty();
    }
}
