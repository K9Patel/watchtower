package com.watchtower.backend.service;

import com.watchtower.backend.entity.Alert;
import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.HealthScoreLog;
import com.watchtower.backend.entity.UsageLog;
import com.watchtower.backend.repository.AlertRepository;
import com.watchtower.backend.repository.DeviceRepository;
import com.watchtower.backend.repository.HealthScoreLogRepository;
import com.watchtower.backend.repository.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkHealthService {

    private static final int HEALTH_WINDOW_SECONDS = 30;
    private static final int PING_TIMEOUT_MS = 1200;
    private static final int TREND_WINDOW_SAMPLES = 12;

    private final UsageLogRepository usageLogRepository;
    private final DeviceRepository deviceRepository;
    private final AlertRepository alertRepository;
    private final HealthScoreLogRepository healthScoreLogRepository;
    private final DashboardWebSocketService webSocketService;

    @Value("${watchtower.alert.threshold:80}")
    private double congestionThresholdPercent;

    private final AtomicReference<HealthSnapshot> latestSnapshot = new AtomicReference<>();
    private final Deque<Integer> recentScores = new ArrayDeque<>();

    @Scheduled(fixedRate = 10_000, initialDelay = 4_000)
    public void scheduledRecalculation() {
        recalculateNow(true);
    }

    @Scheduled(fixedRate = 300_000, initialDelay = 300_000)
    public void scheduledPersistence() {
        persistCurrentSnapshot();
    }

    public synchronized HealthSnapshot recalculateAndBroadcast() {
        return recalculateNow(true);
    }

    public synchronized HealthSnapshot recalculateNow(boolean broadcast) {
        LocalDateTime now = LocalDateTime.now();
        List<UsageLog> recentLogs = usageLogRepository.findByTimestampAfter(now.minusSeconds(HEALTH_WINDOW_SECONDS));
        List<Alert> openAlerts = alertRepository.findByIsResolvedFalseOrderByCreatedAtDesc();
        List<Device> activeDevices = deviceRepository.findByIsActiveTrue();

        short bandwidthScore = calculateBandwidthScore(recentLogs);
        long latencyMillis = measureLatencyMillis();
        short latencyScore = calculateLatencyScore(latencyMillis);
        short alertScore = calculateAlertScore(openAlerts);
        short uptimeScore = calculateUptimeScore(activeDevices, now);

        short compositeScore = calculateCompositeScore(bandwidthScore, latencyScore, alertScore, uptimeScore);
        Trend trend = determineTrend(compositeScore);

        HealthSnapshot snapshot = new HealthSnapshot(
                compositeScore,
                bandwidthScore,
                latencyScore,
                alertScore,
                uptimeScore,
                trend,
                toShortCount(activeDevices.size()),
                toShortCount(openAlerts.size()),
                now
        );

        latestSnapshot.set(snapshot);
        addScoreSample(compositeScore);

        if (broadcast) {
            webSocketService.pushDashboardUpdate(buildWebSocketPayload(snapshot));
        }

        return snapshot;
    }

    public synchronized HealthSnapshot getLatestSnapshot() {
        HealthSnapshot cached = latestSnapshot.get();
        if (cached != null) {
            return cached;
        }
        return recalculateNow(false);
    }

    public synchronized void persistCurrentSnapshot() {
        HealthSnapshot snapshot = latestSnapshot.get();
        if (snapshot == null) {
            snapshot = recalculateNow(false);
        }

        healthScoreLogRepository.save(HealthScoreLog.builder()
                .score(snapshot.score())
                .bandwidthScore(snapshot.bandwidthScore())
                .latencyScore(snapshot.latencyScore())
                .alertScore(snapshot.alertScore())
                .uptimeScore(snapshot.uptimeScore())
                .trend(snapshot.trend().name())
                .activeDeviceCount(snapshot.activeDeviceCount())
                .activeAlertCount(snapshot.activeAlertCount())
                .recordedAt(snapshot.recordedAt())
                .build());
    }

    private short calculateBandwidthScore(List<UsageLog> recentLogs) {
        if (recentLogs.isEmpty()) {
            return (short) 100;
        }

        double totalWindowBandwidth = recentLogs.stream()
                .mapToDouble(UsageLog::getBandwidthPercentage)
                .sum();

        // Keep health sensitivity aligned with congestion threshold used by DiagnosisEngine.
        double effectiveCapacity = Math.max(1.0, congestionThresholdPercent * 1.2);
        double utilizationPercent = (totalWindowBandwidth / effectiveCapacity) * 100.0;
        return clampToScore(100.0 - utilizationPercent);
    }

    private long measureLatencyMillis() {
        long startedAt = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("8.8.8.8", 53), PING_TIMEOUT_MS);
            return System.currentTimeMillis() - startedAt;
        } catch (Exception e) {
            return -1;
        }
    }

    private short calculateLatencyScore(long latencyMillis) {
        if (latencyMillis < 0) {
            return 0;
        }

        return clampToScore(100.0 - (latencyMillis * 0.4));
    }

    private short calculateAlertScore(List<Alert> openAlerts) {
        if (openAlerts.isEmpty()) {
            return (short) 100;
        }

        long low = 0;
        long medium = 0;
        long high = 0;
        long critical = 0;

        for (Alert alert : openAlerts) {
            if (alert.getSeverity() == null) {
                continue;
            }

            switch (alert.getSeverity()) {
                case LOW -> low++;
                case MEDIUM -> medium++;
                case HIGH -> high++;
                case CRITICAL -> critical++;
            }
        }

        double penalty = (low * 5.0)
                + (medium * 12.0)
                + (high * 25.0)
                + (critical * 60.0)
                + (Math.max(0, openAlerts.size() - 1) * 3.0);

        return clampToScore(100.0 - penalty);
    }

    private short calculateUptimeScore(List<Device> activeDevices, LocalDateTime now) {
        if (activeDevices.isEmpty()) {
            return (short) 100;
        }

        long healthyDevices = activeDevices.stream()
                .filter(d -> "ONLINE".equalsIgnoreCase(d.getStatus()))
                .filter(d -> d.getLastSeenAt() == null || d.getLastSeenAt().isAfter(now.minusMinutes(2)))
                .count();

        return clampToScore((healthyDevices * 100.0) / activeDevices.size());
    }

    private short calculateCompositeScore(short bandwidthScore, short latencyScore, short alertScore, short uptimeScore) {
        double composite = (bandwidthScore + latencyScore + alertScore + uptimeScore) / 4.0;
        return clampToScore(composite);
    }

    private Trend determineTrend(short candidateScore) {
        List<Integer> samples = new ArrayList<>(recentScores);
        samples.add((int) candidateScore);

        if (samples.size() < 4) {
            return Trend.STABLE;
        }

        int split = samples.size() / 2;
        double earlyAvg = average(samples.subList(0, split));
        double lateAvg = average(samples.subList(split, samples.size()));
        double delta = lateAvg - earlyAvg;

        if (delta >= 3.0) {
            return Trend.IMPROVING;
        }

        if (delta <= -3.0) {
            return Trend.DEGRADING;
        }

        return Trend.STABLE;
    }

    private double average(List<Integer> values) {
        if (values.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;
        for (Integer value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    private void addScoreSample(short score) {
        recentScores.addLast((int) score);
        while (recentScores.size() > TREND_WINDOW_SAMPLES) {
            recentScores.removeFirst();
        }
    }

    private Map<String, Object> buildWebSocketPayload(HealthSnapshot snapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();

        int score = snapshot.score();
        String trend = snapshot.trend().apiValue();

        payload.put("health_score", score);
        payload.put("trend", trend);
        payload.put("bandwidth_score", (int) snapshot.bandwidthScore());
        payload.put("latency_score", (int) snapshot.latencyScore());
        payload.put("alert_score", (int) snapshot.alertScore());
        payload.put("uptime_score", (int) snapshot.uptimeScore());

        // Backward-compatible aliases for current frontend usage.
        payload.put("healthScore", score);
        payload.put("healthTrend", trend);
        payload.put("systemHealth", score);
        payload.put("timestamp", snapshot.recordedAt().toString());

        return payload;
    }

    private short clampToScore(double value) {
        int rounded = (int) Math.round(value);
        if (rounded < 0) {
            rounded = 0;
        } else if (rounded > 100) {
            rounded = 100;
        }
        return (short) rounded;
    }

    private short toShortCount(int value) {
        int sanitized = Math.max(0, Math.min(Short.MAX_VALUE, value));
        return (short) sanitized;
    }

    public enum Trend {
        IMPROVING,
        STABLE,
        DEGRADING;

        public String apiValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record HealthSnapshot(
            short score,
            short bandwidthScore,
            short latencyScore,
            short alertScore,
            short uptimeScore,
            Trend trend,
            short activeDeviceCount,
            short activeAlertCount,
            LocalDateTime recordedAt
    ) {}
}
