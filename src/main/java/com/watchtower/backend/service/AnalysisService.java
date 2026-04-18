package com.watchtower.backend.service;

import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.UsageLog;
import com.watchtower.backend.repository.DeviceRepository;
import com.watchtower.backend.repository.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.lang.management.ManagementFactory;
import java.net.Socket;
import java.net.InetSocketAddress;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final UsageLogRepository usageLogRepository;
    private final DeviceRepository deviceRepository;
    private final Environment environment;
    private final com.watchtower.backend.repository.AlertRepository alertRepository;

    // AJT — Java Streams + Lambdas:
    // Reads last 60s of logs, calculates total network load %
    public double getTotalLoad() {
        List<UsageLog> recent = usageLogRepository
                .findByTimestampAfter(LocalDateTime.now().minusSeconds(60));

        if (recent.isEmpty())
            return 0.0;

        // Stream: average of all bandwidth percentages = overall network load
        return recent.stream()
                .mapToDouble(UsageLog::getBandwidthPercentage)
                .average()
                .orElse(0.0);
    }

    // AJT — Java Streams:
    // Finds the device consuming the most bandwidth right now
    @Transactional(readOnly = true)
    public Optional<Device> getTopConsumer() {
        List<UsageLog> recent = usageLogRepository
                .findByTimestampAfter(LocalDateTime.now().minusSeconds(60));

        if (recent.isEmpty())
            return Optional.empty();

        // Stream: group by device, sum bytes, find max
        return recent.stream()
                .collect(Collectors.groupingBy(
                        UsageLog::getDevice,
                        Collectors.summingDouble(UsageLog::getBytesUsed)))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }

    // AJT — Java Streams:
    // Counts how many logs of each traffic type in last 60s
    // Returns: {"STREAMING": 8, "BROWSING": 12, "GAMING": 3, ...}
    public Map<String, Long> getTrafficBreakdown() {
        List<UsageLog> recent = usageLogRepository
                .findByTimestampAfter(LocalDateTime.now().minusSeconds(60));

        return recent.stream()
                .collect(Collectors.groupingBy(
                        UsageLog::getTrafficType,
                        Collectors.counting()));
    }

    // AJT — Java Streams:
    // Calculates what % of total bandwidth each device is using
    @Transactional(readOnly = true)
    public Map<String, Double> getBandwidthSharePerDevice() {
        List<UsageLog> recent = usageLogRepository
                .findByTimestampAfter(LocalDateTime.now().minusSeconds(60));

        if (recent.isEmpty())
            return Collections.emptyMap();

        double totalBytes = recent.stream()
                .mapToDouble(UsageLog::getBytesUsed)
                .sum();

        // group by device name, sum bytes, convert to percentage of total
        return recent.stream()
                .collect(Collectors.groupingBy(
                        log -> log.getDevice().getDeviceName(),
                        Collectors.summingDouble(UsageLog::getBytesUsed)))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> Math.round((e.getValue() / totalBytes) * 10000.0) / 100.0));
    }

    // Full summary object — used by StatsController GET /api/stats/summary
    @Transactional(readOnly = true)
    public Map<String, Object> getFullSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();

        double totalLoad = getTotalLoad();
        Optional<Device> topConsumer = getTopConsumer();
        long onlineCount = deviceRepository.findByIsActiveTrue().stream()
                .filter(d -> "ONLINE".equals(d.getStatus()))
                .count();

        summary.put("totalLoadPercent", Math.round(totalLoad * 100.0) / 100.0);
        summary.put("topConsumer", topConsumer.map(Device::getDeviceName).orElse("N/A"));
        summary.put("onlineDevices", onlineCount);
        summary.put("totalDevices", deviceRepository.count());
        summary.put("trafficBreakdown", getTrafficBreakdown());
        summary.put("bandwidthShare", getBandwidthSharePerDevice());
        // AJT: dynamically read active profile instead of hardcoding
        String activeProfile = java.util.Arrays.stream(environment.getActiveProfiles())
                .findFirst().orElse("default");
        summary.put("activeProfile", activeProfile);
        summary.put("timestamp", LocalDateTime.now().toString());

        long unresolvedAlerts = alertRepository.countByIsResolvedFalse();
        double systemHealth = Math.max(0, 100.0 - (totalLoad / 2.0) - (unresolvedAlerts * 5));
        summary.put("systemHealth", Math.round(systemHealth * 10.0) / 10.0);

        long jvmUptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        long seconds = jvmUptimeMillis / 1000 % 60;
        long minutes = jvmUptimeMillis / (60 * 1000) % 60;
        long hours = jvmUptimeMillis / (60 * 60 * 1000);
        summary.put("uptime", String.format("%dh %dm %ds", hours, minutes, seconds));

        long startTime = System.currentTimeMillis();
        long responseTime = 0;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("8.8.8.8", 53), 1000); // physical TCP ping to Google DNS
            responseTime = System.currentTimeMillis() - startTime;
        } catch (Exception e) {
            responseTime = -1; // Offline
        }
        summary.put("avgResponseTime", responseTime);

        return summary;
    }
}
