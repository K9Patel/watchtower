package com.watchtower.backend.service;

import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.HealthScoreLog;
import com.watchtower.backend.entity.UsageLog;
import com.watchtower.backend.repository.DeviceRepository;
import com.watchtower.backend.repository.HealthScoreLogRepository;
import com.watchtower.backend.repository.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryAnalysisService {

    private static final DateTimeFormatter HEALTH_TIME_LABEL = DateTimeFormatter.ofPattern("HH:mm");

    private final UsageLogRepository usageLogRepository;
    private final DeviceRepository deviceRepository;
    private final HealthScoreLogRepository healthScoreLogRepository;
    private final NetworkHealthService networkHealthService;

    // AJT Unit 8 — JPQL @Query:
    // GROUP BY day — returns daily total MB for last 7 days
    // Used by history chart on dashboard
    public List<Map<String, Object>> getWeeklyDailyTotals() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusDays(7);
        List<Object[]> raw = usageLogRepository.findDailyTotals(since);
        Map<String, Double> dailyHealthScores = healthScoreLogRepository.findDailyAverageScores(since).stream()
                .collect(Collectors.toMap(
                        row -> row[0].toString(),
                        row -> round1(((Number) row[1]).doubleValue()),
                        (a, b) -> b,
                        LinkedHashMap::new
                ));

        double latestHealth = networkHealthService.getLatestSnapshot().score();
        double hourlyHealth = getHourlyHealthAverage(60);
        String todayKey = LocalDate.now().toString();

        return raw.stream().map(row -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            String dateKey = row[0].toString();
            double dayHealth = dailyHealthScores.getOrDefault(dateKey, latestHealth);

            entry.put("date", dateKey);
            entry.put("totalTraffic", round2(((Number) row[1]).doubleValue()));
            entry.put("peakLoad", row.length > 2 && row[2] != null ? round2(((Number) row[2]).doubleValue()) : 0.0);
            entry.put("healthScore", round1(dayHealth));
            entry.put("hourlyHealthScore", todayKey.equals(dateKey) ? hourlyHealth : round1(dayHealth));
            return entry;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getHourlyHealthScoreTimeline(int minutes) {
        int windowMinutes = Math.max(15, Math.min(minutes, 240));
        LocalDateTime since = LocalDateTime.now().minusMinutes(windowMinutes);

        List<HealthScoreLog> persisted = healthScoreLogRepository.findByRecordedAtAfterOrderByRecordedAtAsc(since);
        List<Map<String, Object>> timeline = persisted.stream()
                .map(this::toHealthPoint)
                .collect(Collectors.toCollection(ArrayList::new));

        NetworkHealthService.HealthSnapshot latest = networkHealthService.getLatestSnapshot();
        if (latest.recordedAt().isAfter(since)) {
            Map<String, Object> latestPoint = toHealthPoint(latest);

            if (timeline.isEmpty()) {
                timeline.add(latestPoint);
            } else {
                String lastTimestamp = String.valueOf(timeline.get(timeline.size() - 1).get("timestamp"));
                LocalDateTime lastTime = LocalDateTime.parse(lastTimestamp);

                if (latest.recordedAt().isAfter(lastTime.plusSeconds(30))) {
                    timeline.add(latestPoint);
                } else {
                    timeline.set(timeline.size() - 1, latestPoint);
                }
            }
        }

        return timeline;
    }

    // AJT Unit 8 — JPQL @Query:
    // GROUP BY hour — returns avg bandwidth % per hour of day (0-23)
    // Used by peak hours heatmap on dashboard
    public Map<Integer, Double> getPeakHoursLastWeek() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        List<Object[]> raw = usageLogRepository.findPeakHours(since);

        Map<Integer, Double> peakHours = new LinkedHashMap<>();

        // initialize all 24 hours to 0
        for (int i = 0; i < 24; i++) {
            peakHours.put(i, 0.0);
        }

        // fill in actual values from query
        raw.forEach(row -> {
            int hour = ((Number) row[0]).intValue();
            double avgLoad = ((Number) row[1]).doubleValue();
            peakHours.put(hour, avgLoad);
        });

        return peakHours;
    }

    // AJT — Java Streams:
    // Per-device summary using Streams + Lambdas on query results
    // Returns max, min, avg, total for each device
    public List<Map<String, Object>> getPerDeviceSummary() {
        List<Device> devices = deviceRepository.findByIsActiveTrue();
        LocalDateTime since = LocalDateTime.now().minusDays(7);

        return devices.stream().map(device -> {
            List<UsageLog> logs = usageLogRepository
                .findByDeviceAndTimestampAfter(device, since);

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("deviceName", device.getDeviceName());
            summary.put("ipAddress", device.getIpAddress());
            summary.put("status", device.getStatus());

            if (logs.isEmpty()) {
                summary.put("totalTraffic", 0.0);
                summary.put("avgLoad", 0.0);
                summary.put("maxMB", 0.0);
                summary.put("minMB", 0.0);
                summary.put("readingCount", 0);
            } else {
                // Java Streams — mapToDouble + summaryStatistics
                DoubleSummaryStatistics stats = logs.stream()
                    .mapToDouble(UsageLog::getBytesUsed)
                    .summaryStatistics();
                DoubleSummaryStatistics loadStats = logs.stream()
                    .mapToDouble(UsageLog::getBandwidthPercentage)
                    .summaryStatistics();

                summary.put("totalTraffic", Math.round(stats.getSum()  * 100.0) / 100.0);
                summary.put("avgLoad",      Math.round(loadStats.getAverage() * 100.0) / 100.0);
                summary.put("maxMB",        Math.round(stats.getMax()  * 100.0) / 100.0);
                summary.put("minMB",        Math.round(stats.getMin()  * 100.0) / 100.0);
                summary.put("readingCount", stats.getCount());
            }

            return summary;
        }).collect(Collectors.toList());
    }

    // Returns last N usage logs for a specific device
    public List<UsageLog> getDeviceHistory(Long deviceId, int limit) {
        return deviceRepository.findById(deviceId).map(device ->
            usageLogRepository.findTop100ByDeviceOrderByTimestampDesc(device)
                .stream().limit(limit).collect(Collectors.toList())
        ).orElse(Collections.emptyList());
    }

    private double getHourlyHealthAverage(int minutes) {
        List<Map<String, Object>> points = getHourlyHealthScoreTimeline(minutes);
        if (points.isEmpty()) {
            return round1(networkHealthService.getLatestSnapshot().score());
        }

        double average = points.stream()
                .map(p -> (Number) p.get("healthScore"))
                .mapToDouble(Number::doubleValue)
                .average()
                .orElse(networkHealthService.getLatestSnapshot().score());

        return round1(average);
    }

    private Map<String, Object> toHealthPoint(HealthScoreLog log) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("timestamp", log.getRecordedAt().toString());
        point.put("label", log.getRecordedAt().format(HEALTH_TIME_LABEL));
        point.put("healthScore", Integer.valueOf(log.getScore()));
        point.put("trend", log.getTrend() != null ? log.getTrend().toLowerCase(Locale.ROOT) : "stable");
        return point;
    }

    private Map<String, Object> toHealthPoint(NetworkHealthService.HealthSnapshot snapshot) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("timestamp", snapshot.recordedAt().toString());
        point.put("label", snapshot.recordedAt().format(HEALTH_TIME_LABEL));
        point.put("healthScore", (int) snapshot.score());
        point.put("trend", snapshot.trend().apiValue());
        return point;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}