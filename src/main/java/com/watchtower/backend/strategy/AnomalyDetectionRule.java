package com.watchtower.backend.strategy;

import com.watchtower.backend.entity.Alert;
import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.DeviceBaseline;
import com.watchtower.backend.entity.UsageLog;
import com.watchtower.backend.repository.DeviceBaselineRepository;
import com.watchtower.backend.repository.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * AJT — Strategy Pattern: Rule 4 — Baseline Anomaly Detection
 *
 * Compares current bandwidth against the device's learned baseline for
 * this hour-of-day. Only fires AFTER the 1-day learning period is complete
 * (device.baselineReady == true).
 *
 * Threshold: current bandwidth > baseline avg + 1.5 × stddev
 *            AND absolute delta >= 15 percentage points
 *
 * Severity: CRITICAL
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnomalyDetectionRule implements DiagnosisRule {

    /** How many standard deviations above mean triggers an alert */
    private static final double STDDEV_MULTIPLIER = 1.5;

    /** Minimum absolute bandwidth delta to avoid false positives on low baselines */
    private static final double MIN_ABSOLUTE_DELTA = 15.0;

    /** Cold-start fallback window for devices still in learning mode */
    private static final int COLD_START_WINDOW_HOURS = 3;
    private static final int COLD_START_MIN_SAMPLES = 12;
    private static final double COLD_START_FACTOR = 3.0;
    private static final double COLD_START_MIN_DELTA = 20.0;

    private final DeviceBaselineRepository baselineRepository;
    private final UsageLogRepository usageLogRepository;

    @Override
    public Optional<Alert> evaluate(Device device, List<UsageLog> recentLogs) {
        if (recentLogs.isEmpty()) return Optional.empty();

        // ── Current bandwidth: average of recent 30-second window ────────────
        double current = recentLogs.stream()
                .mapToDouble(UsageLog::getBandwidthPercentage)
                .average()
                .orElse(0.0);

        // ── Cold-start fallback: evaluate anomaly using recent real history ───
        if (!Boolean.TRUE.equals(device.getBaselineReady())) {
            return evaluateColdStartAnomaly(device, current);
        }

        // ── Lookup baseline for current hour ─────────────────────────────────
        int currentHour = LocalDateTime.now().getHour();
        short hourSlot = (short) currentHour;
        Optional<DeviceBaseline> baselineOpt = baselineRepository
            .findByDeviceIdAndHourOfDay(device.getId(), hourSlot);

        if (baselineOpt.isEmpty()) {
            // No baseline data for this hour slot yet — can't evaluate
            return Optional.empty();
        }

        DeviceBaseline baseline = baselineOpt.get();
        double baselineAvg = baseline.getAvgBandwidth();
        double baselineStddev = baseline.getStddevBandwidth();

        // ── Skip if baseline avg is near zero (no meaningful data) ───────────
        if (baselineAvg <= 0.5) return Optional.empty();

        // ── Anomaly threshold: avg + 1.5 × stddev ───────────────────────────
        // Use a minimum stddev floor of 5.0 to avoid overly sensitive alerts
        double effectiveStddev = Math.max(baselineStddev, 5.0);
        double threshold = baselineAvg + (STDDEV_MULTIPLIER * effectiveStddev);
        double absoluteDelta = current - baselineAvg;

        if (current > threshold && absoluteDelta >= MIN_ABSOLUTE_DELTA) {
            String deviceLabel = device.getDeviceName() != null
                    ? device.getDeviceName()
                    : device.getIpAddress();

            return Optional.of(Alert.builder()
                    .device(device)
                    .alertType("ANOMALY")
                    .severity(Alert.Severity.CRITICAL)
                    .message(String.format(
                            "%s behaving abnormally (baseline: %.1f%%, current: %.1f%%, threshold: %.1f%%)",
                            deviceLabel, baselineAvg, current, threshold))
                    .build());
        }

        return Optional.empty();
    }

    private Optional<Alert> evaluateColdStartAnomaly(Device device, double current) {
        LocalDateTime recentStart = LocalDateTime.now().minusSeconds(30);
        LocalDateTime coldStartSince = LocalDateTime.now().minusHours(COLD_START_WINDOW_HOURS);

        List<Double> history = usageLogRepository
                .findByDeviceAndTimestampAfter(device, coldStartSince)
                .stream()
                .filter(log -> log.getTimestamp().isBefore(recentStart))
                .map(UsageLog::getBandwidthPercentage)
                .filter(value -> value != null && value >= 0)
                .sorted(Comparator.naturalOrder())
                .toList();

        if (history.size() < COLD_START_MIN_SAMPLES) {
            return Optional.empty();
        }

        double median = median(history);
        double delta = current - median;

        if (median <= 0.0) {
            return Optional.empty();
        }

        if (current > (median * COLD_START_FACTOR) && delta >= COLD_START_MIN_DELTA) {
            String deviceLabel = device.getDeviceName() != null
                    ? device.getDeviceName()
                    : device.getIpAddress();

            return Optional.of(Alert.builder()
                    .device(device)
                    .alertType("ANOMALY")
                    .severity(Alert.Severity.HIGH)
                    .message(String.format(
                            "%s traffic anomaly detected (cold-start mode: median %.1f%% over last %dh, current %.1f%%).",
                            deviceLabel, median, COLD_START_WINDOW_HOURS, current))
                    .build());
        }

        return Optional.empty();
    }

    private double median(List<Double> values) {
        int n = values.size();
        if (n == 0) {
            return 0.0;
        }
        if (n % 2 == 1) {
            return values.get(n / 2);
        }
        return (values.get((n / 2) - 1) + values.get(n / 2)) / 2.0;
    }
}
