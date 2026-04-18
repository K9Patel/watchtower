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
 * AJT — Strategy Pattern: Rule 3 — Streaming Overload
 * Fires when streaming traffic exceeds 60% of total traffic in the last 30 seconds.
 * Severity: HIGH
 */
@Component
@RequiredArgsConstructor
public class StreamingOverloadRule implements DiagnosisRule {

        private static final double STREAMING_SHARE_THRESHOLD = 60.0;

        private final UsageLogRepository usageLogRepository;

    @Override
    public Optional<Alert> evaluate(Device device, List<UsageLog> recentLogs) {
        List<UsageLog> allRecent = usageLogRepository.findByTimestampAfter(LocalDateTime.now().minusSeconds(30));
        if (allRecent.isEmpty()) return Optional.empty();

        double totalBytes = allRecent.stream().mapToDouble(UsageLog::getBytesUsed).sum();
        if (totalBytes <= 0.0) return Optional.empty();

        double streamingBytes = allRecent.stream()
                .filter(log -> "STREAMING".equalsIgnoreCase(log.getTrafficType()))
                .mapToDouble(UsageLog::getBytesUsed)
                .sum();

        double streamingShare = (streamingBytes / totalBytes) * 100.0;

        double deviceStreamingBytes = recentLogs.stream()
                .filter(log -> "STREAMING".equalsIgnoreCase(log.getTrafficType()))
                .mapToDouble(UsageLog::getBytesUsed)
                .sum();

        if (streamingShare > STREAMING_SHARE_THRESHOLD && deviceStreamingBytes > 0.0) {
            return Optional.of(Alert.builder()
                    .device(device)
                    .alertType("STREAMING_OVERLOAD")
                    .severity(Alert.Severity.HIGH)
                    .message(String.format(
                            "Video streaming overloading network (%.1f%% of total traffic).",
                            streamingShare))
                    .build());
        }

        return Optional.empty();
    }
}
