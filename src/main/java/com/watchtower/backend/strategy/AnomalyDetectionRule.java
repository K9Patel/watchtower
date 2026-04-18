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
 * AJT — Strategy Pattern: Rule 4 — Baseline Anomaly Detection
 * Fires when current 30-second behavior significantly exceeds the device's 7-day baseline.
 * Severity: CRITICAL
 */
@Component
@RequiredArgsConstructor
public class AnomalyDetectionRule implements DiagnosisRule {

     private static final double MULTIPLIER_THRESHOLD = 2.0;
     private static final double MIN_ABSOLUTE_DELTA = 20.0;

    private final UsageLogRepository usageLogRepository;

    @Override
    public Optional<Alert> evaluate(Device device, List<UsageLog> recentLogs) {
        if (recentLogs.isEmpty()) return Optional.empty();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sevenDaysAgo = now.minusDays(7);
        LocalDateTime recentStart = now.minusSeconds(30);

        List<UsageLog> baselineHistory = usageLogRepository
            .findByDeviceAndTimestampAfter(device, sevenDaysAgo)
            .stream()
            .filter(log -> log.getTimestamp().isBefore(recentStart))
            .toList();

        if (baselineHistory.size() < 20) return Optional.empty();

        double baselineAvg = baselineHistory.stream()
            .mapToDouble(UsageLog::getBandwidthPercentage)
            .average()
            .orElse(0.0);

        if (baselineAvg <= 0.0) return Optional.empty();

        double current = recentLogs.stream()
                .mapToDouble(UsageLog::getBandwidthPercentage)
            .average()
            .orElse(0.0);

        if (current > (baselineAvg * MULTIPLIER_THRESHOLD)
            && (current - baselineAvg) >= MIN_ABSOLUTE_DELTA) {
            return Optional.of(Alert.builder()
                    .device(device)
                    .alertType("ANOMALY")
                .severity(Alert.Severity.CRITICAL)
                    .message(String.format(
                    "%s behaving abnormally (%.1f%% now vs %.1f%% 7-day baseline).",
                    device.getDeviceName(), current, baselineAvg))
                    .build());
        }

        return Optional.empty();
    }
}
