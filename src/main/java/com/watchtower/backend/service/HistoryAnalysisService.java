package com.watchtower.backend.service;

import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.UsageLog;
import com.watchtower.backend.repository.DeviceRepository;
import com.watchtower.backend.repository.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryAnalysisService {

    private final UsageLogRepository usageLogRepository;
    private final DeviceRepository deviceRepository;

    private LocalDateTime applyScopeStart(LocalDateTime defaultSince, LocalDateTime scopeStart) {
        if (scopeStart == null) {
            return defaultSince;
        }
        return scopeStart.isAfter(defaultSince) ? scopeStart : defaultSince;
    }

    // AJT Unit 8 — JPQL @Query:
    // GROUP BY day — returns daily total MB for last 7 days
    // Used by history chart on dashboard
    public List<Map<String, Object>> getWeeklyDailyTotals() {
        return getWeeklyDailyTotals(null);
    }

    public List<Map<String, Object>> getWeeklyDailyTotals(LocalDateTime scopeStart) {
        LocalDateTime since = applyScopeStart(LocalDateTime.now().minusDays(7), scopeStart);
        List<Object[]> raw = usageLogRepository.findDailyTotals(since);
        return mapDailyTotals(raw);
    }

    public List<Map<String, Object>> getDailyTotalsBetween(LocalDateTime since, LocalDateTime until) {
        List<Object[]> raw = usageLogRepository.findDailyTotalsBetween(since, until);
        return mapDailyTotals(raw);
    }

    private List<Map<String, Object>> mapDailyTotals(List<Object[]> raw) {

        return raw.stream().map(row -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", row[0].toString());
            entry.put("totalTraffic", ((Number) row[1]).doubleValue());
            entry.put("peakLoad", row.length > 2 && row[2] != null ? ((Number) row[2]).doubleValue() : 0.0);
            return entry;
        }).collect(Collectors.toList());
    }

    // AJT Unit 8 — JPQL @Query:
    // GROUP BY hour — returns avg bandwidth % per hour of day (0-23)
    // Used by peak hours heatmap on dashboard
    public Map<Integer, Double> getPeakHoursLastWeek() {
        return getPeakHoursLastWeek(null);
    }

    public Map<Integer, Double> getPeakHoursLastWeek(LocalDateTime scopeStart) {
        LocalDateTime since = applyScopeStart(LocalDateTime.now().minusDays(7), scopeStart);
        List<Object[]> raw = usageLogRepository.findPeakHours(since);
        return mapPeakHours(raw);
    }

    public Map<Integer, Double> getPeakHoursBetween(LocalDateTime since, LocalDateTime until) {
        List<Object[]> raw = usageLogRepository.findPeakHoursBetween(since, until);
        return mapPeakHours(raw);
    }

    private Map<Integer, Double> mapPeakHours(List<Object[]> raw) {

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
        return getPerDeviceSummary(null);
    }

    public List<Map<String, Object>> getPerDeviceSummary(LocalDateTime scopeStart) {
        List<Device> devices = deviceRepository.findByIsActiveTrue();
        LocalDateTime since = applyScopeStart(LocalDateTime.now().minusDays(7), scopeStart);
        return getPerDeviceSummaryBetween(devices, since, null);
    }

    public List<Map<String, Object>> getPerDeviceSummaryBetween(LocalDateTime since, LocalDateTime until) {
        List<Device> devices = deviceRepository.findByIsActiveTrue();
        return getPerDeviceSummaryBetween(devices, since, until);
    }

    private List<Map<String, Object>> getPerDeviceSummaryBetween(List<Device> devices, LocalDateTime since, LocalDateTime until) {

        return devices.stream().map(device -> {
            List<UsageLog> logs = until == null
                    ? usageLogRepository.findByDeviceAndTimestampAfter(device, since)
                    : usageLogRepository.findByDeviceAndTimestampBetween(device, since, until);

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
        return getDeviceHistory(deviceId, limit, null);
    }

    public List<UsageLog> getDeviceHistory(Long deviceId, int limit, LocalDateTime scopeStart) {
        LocalDateTime since = applyScopeStart(LocalDateTime.now().minusDays(7), scopeStart);
        return deviceRepository.findById(deviceId).map(device ->
            usageLogRepository.findTop100ByDeviceAndTimestampAfterOrderByTimestampDesc(device, since)
                .stream().limit(limit).collect(Collectors.toList())
        ).orElse(Collections.emptyList());
    }
}