package com.watchtower.backend.service;

import com.watchtower.backend.entity.Alert;
import com.watchtower.backend.entity.UsageLog;
import com.watchtower.backend.repository.AlertRepository;
import com.watchtower.backend.repository.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IncidentTimelineService {

    private static final DateTimeFormatter MARKER_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final UsageLogRepository usageLogRepository;
    private final AlertRepository alertRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> buildTimeline(int windowMinutes) {
        int minutes = Math.max(10, Math.min(windowMinutes, 180));
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        LocalDateTime since = now.minusMinutes(minutes - 1L);

        List<UsageLog> logs = usageLogRepository.findByTimestampAfterOrderByTimestampAsc(since);
        List<Alert> alerts = alertRepository.findByCreatedAtAfterOrderByCreatedAtAsc(since);

        Map<LocalDateTime, List<UsageLog>> byMinute = logs.stream()
                .collect(Collectors.groupingBy(
                        l -> l.getTimestamp().withSecond(0).withNano(0),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<Map<String, Object>> snapshots = new ArrayList<>();
        String lastTopConsumer = "N/A";

        for (int i = 0; i < minutes; i++) {
            LocalDateTime minute = since.plusMinutes(i);
            List<UsageLog> minuteLogs = byMinute.getOrDefault(minute, List.of());

            double avgLoad = minuteLogs.stream()
                    .mapToDouble(UsageLog::getBandwidthPercentage)
                    .average()
                    .orElse(0.0);

            Map<String, Double> bytesByDevice = minuteLogs.stream()
                    .collect(Collectors.groupingBy(
                            log -> log.getDevice().getDeviceName() != null ? log.getDevice().getDeviceName() : "Unknown",
                            Collectors.summingDouble(UsageLog::getBytesUsed)
                    ));

            String topConsumer = bytesByDevice.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(lastTopConsumer);

            int onlineDevices = (int) minuteLogs.stream()
                    .collect(Collectors.groupingBy(log -> log.getDevice().getId()))
                    .values().stream()
                    .map(deviceLogs -> deviceLogs.stream().mapToDouble(UsageLog::getBytesUsed).sum())
                    .filter(total -> total > 0.01)
                    .count();

            int totalDevices = (int) minuteLogs.stream()
                    .map(log -> log.getDevice().getId())
                    .distinct()
                    .count();

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("timestamp", minute.toString());
            snapshot.put("marker", minute.format(MARKER_FORMAT));
            snapshot.put("totalLoadPercent", round2(avgLoad));
            snapshot.put("topConsumer", topConsumer != null ? topConsumer : "N/A");
            snapshot.put("onlineDevices", onlineDevices);
            snapshot.put("totalDevices", totalDevices);
            snapshots.add(snapshot);

            if (topConsumer != null) {
                lastTopConsumer = topConsumer;
            }
        }

        List<Map<String, Object>> events = buildEvents(snapshots, byMinute, alerts);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("windowMinutes", minutes);
        payload.put("startedAt", since.toString());
        payload.put("endedAt", now.toString());
        payload.put("snapshots", snapshots);
        payload.put("events", events);
        return payload;
    }

    private List<Map<String, Object>> buildEvents(
            List<Map<String, Object>> snapshots,
            Map<LocalDateTime, List<UsageLog>> byMinute,
            List<Alert> alerts
    ) {
        List<Map<String, Object>> events = new ArrayList<>();

        String previousTop = null;
        for (Map<String, Object> snapshot : snapshots) {
            String currentTop = String.valueOf(snapshot.get("topConsumer"));
            if (previousTop != null && !"N/A".equals(currentTop) && !previousTop.equals(currentTop)) {
                events.add(eventOf(
                        String.valueOf(snapshot.get("timestamp")),
                        "TOP_CONSUMER_CHANGED",
                        "Top consumer changed from " + previousTop + " to " + currentTop
                ));
            }
            previousTop = currentTop;
        }

        // Detect offline transitions via snapshot-level online count drop.
        for (int i = 1; i < snapshots.size(); i++) {
            Map<String, Object> prev = snapshots.get(i - 1);
            Map<String, Object> curr = snapshots.get(i);

            int prevOnline = ((Number) prev.get("onlineDevices")).intValue();
            int currOnline = ((Number) curr.get("onlineDevices")).intValue();

            if (currOnline < prevOnline) {
                int dropped = prevOnline - currOnline;
                events.add(eventOf(
                        String.valueOf(curr.get("timestamp")),
                        "DEVICE_OFFLINE",
                        dropped == 1
                                ? "1 device went offline"
                                : dropped + " devices went offline"
                ));
            }
        }

        for (Alert alert : alerts) {
            if (alert.getAlertType() != null && alert.getAlertType().toUpperCase().contains("ANOMALY")) {
                events.add(eventOf(
                        alert.getCreatedAt().withSecond(0).withNano(0).toString(),
                        "ANOMALY_STARTED",
                        alert.getMessage() != null ? alert.getMessage() : "Anomaly detected"
                ));
            }
        }

        events.sort(Comparator.comparing(e -> String.valueOf(e.get("timestamp"))));
        return events;
    }

    private Map<String, Object> eventOf(String timestamp, String type, String message) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("timestamp", timestamp);
        e.put("type", type);
        e.put("message", message);
        return e;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
