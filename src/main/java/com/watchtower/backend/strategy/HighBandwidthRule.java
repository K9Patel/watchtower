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
 * AJT — Strategy Pattern: Rule 1 — High Bandwidth
 * Fires when one device uses > 50% of total network traffic in the last 30 seconds.
 * Severity: HIGH
 */
@Component
@RequiredArgsConstructor
public class HighBandwidthRule implements DiagnosisRule {

    private static final double SHARE_THRESHOLD = 50.0;

    private final UsageLogRepository usageLogRepository;

    @Override
    public Optional<Alert> evaluate(Device device, List<UsageLog> recentLogs) {
        if (recentLogs.isEmpty()) return Optional.empty();

        double deviceBytes = recentLogs.stream()
            .mapToDouble(UsageLog::getBytesUsed)
            .sum();

        if (deviceBytes <= 0) return Optional.empty();

        double totalBytes = usageLogRepository.findByTimestampAfter(LocalDateTime.now().minusSeconds(30))
            .stream()
            .mapToDouble(UsageLog::getBytesUsed)
            .sum();

        if (totalBytes <= 0) return Optional.empty();

        double sharePercent = (deviceBytes / totalBytes) * 100.0;

        if (sharePercent > SHARE_THRESHOLD) {
            double rounded = Math.round(sharePercent * 10.0) / 10.0;
            return Optional.of(Alert.builder()
                    .device(device)
                    .alertType("CONGESTION")
                    .severity(Alert.Severity.HIGH)
                    .message(String.format(
                    "%s is monopolizing bandwidth (%.1f%%). Possible large download.",
                            device.getDeviceName(), rounded))
                    .build());
        }

        return Optional.empty();
    }
}
