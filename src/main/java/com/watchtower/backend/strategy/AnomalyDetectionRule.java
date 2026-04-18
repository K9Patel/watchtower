package com.watchtower.backend.strategy;

import com.watchtower.backend.entity.Alert;
import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.UsageLog;
import com.watchtower.backend.repository.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * AJT — Strategy Pattern: Rule 4 — Z-Score Anomaly Detection
 * Uses statistical Z-Score on the last 100 readings.
 * If current bandwidth deviates more than 2.5 standard deviations from the mean,
 * it flags a statistical anomaly (unusual behaviour even if raw % looks ok).
 * Severity: CRITICAL if Z > 3.5, else HIGH
 */
@Component
@RequiredArgsConstructor
public class AnomalyDetectionRule implements DiagnosisRule {

    private static final double Z_SCORE_THRESHOLD = 2.5;

    private final UsageLogRepository usageLogRepository;

    @Override
    public Optional<Alert> evaluate(Device device, List<UsageLog> recentLogs) {
        if (recentLogs.isEmpty()) return Optional.empty();

        // Fetch last 100 readings for Z-Score baseline
        List<UsageLog> history = usageLogRepository
                .findTop100ByDeviceOrderByTimestampDesc(device);

        if (history.size() < 5) return Optional.empty(); // need enough data

        double[] values = history.stream()
                .mapToDouble(UsageLog::getBandwidthPercentage)
                .toArray();

        double mean = computeMean(values);
        double stdDev = computeStdDev(values, mean);

        if (stdDev == 0) return Optional.empty();

        // Current = most recent log in the window
        double current = recentLogs.stream()
                .mapToDouble(UsageLog::getBandwidthPercentage)
                .max().orElse(0.0);

        double zScore = Math.abs((current - mean) / stdDev);

        if (zScore >= Z_SCORE_THRESHOLD) {
            Alert.Severity severity = zScore >= 3.5 ? Alert.Severity.CRITICAL : Alert.Severity.HIGH;
            return Optional.of(Alert.builder()
                    .device(device)
                    .alertType("ANOMALY")
                    .severity(severity)
                    .message(String.format(
                            "Z-Score anomaly on %s: z=%.2f (threshold %.1f). " +
                            "Current bandwidth %.1f%% vs baseline mean %.1f%%.",
                            device.getDeviceName(), zScore, Z_SCORE_THRESHOLD, current, mean))
                    .build());
        }

        return Optional.empty();
    }

    private double computeMean(double[] values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private double computeStdDev(double[] values, double mean) {
        double sumSq = 0;
        for (double v : values) sumSq += (v - mean) * (v - mean);
        return Math.sqrt(sumSq / values.length);
    }
}
