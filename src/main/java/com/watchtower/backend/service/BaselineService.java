package com.watchtower.backend.service;

import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.DeviceBaseline;
import com.watchtower.backend.entity.UsageLog;
import com.watchtower.backend.repository.DeviceBaselineRepository;
import com.watchtower.backend.repository.DeviceRepository;
import com.watchtower.backend.repository.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Feature 4 — Baseline Learning Service
 *
 * Runs every 10 minutes to build per-device hourly bandwidth baselines.
 * Learning period: 1 day (configurable). After 1 day of data collection,
 * the device is marked baseline_ready = true and the AnomalyDetectionRule
 * begins comparing live traffic against the learned baseline.
 *
 * Each device gets 24 baseline rows — one per hour of day.
 * Each row stores: avg_bandwidth, stddev_bandwidth, sample_count.
 *
 * AJT — @Scheduled + Java Streams + Statistics
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BaselineService {

    /** Learning period in days — set to 1 for fast development */
    private static final int LEARNING_PERIOD_DAYS = 1;

    /** Minimum samples per hour slot before baseline is considered valid */
    private static final int MIN_SAMPLES_PER_SLOT = 1;

    /** Minimum hour slots that must have data before baseline_ready = true */
    private static final int MIN_POPULATED_SLOTS = 1;

    private final DeviceRepository deviceRepository;
    private final DeviceBaselineRepository baselineRepository;
    private final UsageLogRepository usageLogRepository;

    /**
     * Scheduled baseline builder — runs every 10 minutes.
     * For each device:
     *   1. Initialize baseline_since if not yet set
     *   2. Aggregate usage_log data by hour_of_day
     *   3. Compute avg + stddev for each hour slot
     *   4. Mark baseline_ready once learning period is complete
     */
    @Scheduled(fixedRate = 600_000, initialDelay = 60_000) // every 10 min, first run after 1 min
    @Transactional
    public void buildBaselines() {
        List<Device> allDevices = deviceRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        int updated = 0;
        int newlyReady = 0;

        for (Device device : allDevices) {
            try {
                // ── Step 1: Initialize baseline_since if not set ──────────────
                if (device.getBaselineSince() == null) {
                    device.setBaselineSince(now);
                    deviceRepository.save(device);
                    log.info("BaselineService: started learning period for device '{}' (id={})",
                            device.getDeviceName(), device.getId());
                    continue; // No data to aggregate yet on first encounter
                }

                // ── Step 2: Fetch all usage logs since baseline_since ─────────
                List<UsageLog> logs = usageLogRepository
                        .findByDeviceAndTimestampAfter(device, device.getBaselineSince());

                if (logs.isEmpty()) continue;

                // ── Step 3: Group by hour_of_day and compute statistics ───────
                Map<Integer, List<UsageLog>> byHour = logs.stream()
                        .collect(Collectors.groupingBy(
                                logEntry -> logEntry.getTimestamp().getHour()));

                for (Map.Entry<Integer, List<UsageLog>> entry : byHour.entrySet()) {
                    int hour = entry.getKey();
                    short hourSlot = (short) hour;
                    List<UsageLog> hourLogs = entry.getValue();

                    double avg = hourLogs.stream()
                            .mapToDouble(UsageLog::getBandwidthPercentage)
                            .average()
                            .orElse(0.0);

                    double stddev = computeStdDev(hourLogs, avg);

                    // Upsert: find existing baseline row or create new one
                    DeviceBaseline baseline = baselineRepository
                            .findByDeviceIdAndHourOfDay(device.getId(), hourSlot)
                            .orElse(DeviceBaseline.builder()
                                    .device(device)
                                .hourOfDay(hourSlot)
                                    .build());

                    baseline.setAvgBandwidth(Math.round(avg * 100.0) / 100.0);
                    baseline.setStddevBandwidth(Math.round(stddev * 100.0) / 100.0);
                    baseline.setSampleCount(hourLogs.size());
                    baseline.setLastUpdated(now);

                    baselineRepository.save(baseline);
                }

                updated++;

                // ── Step 4: Check if learning period is complete ──────────────
                if (!Boolean.TRUE.equals(device.getBaselineReady())) {
                    boolean timePassed = device.getBaselineSince()
                            .plusDays(LEARNING_PERIOD_DAYS)
                            .isBefore(now);

                    long populatedSlots = baselineRepository.countByDeviceId(device.getId());
                    boolean enoughSlots = populatedSlots >= MIN_POPULATED_SLOTS;

                    if (timePassed && enoughSlots) {
                        device.setBaselineReady(true);
                        deviceRepository.save(device);
                        newlyReady++;
                        log.info("BaselineService: ✅ baseline READY for device '{}' (id={}, slots={}/24)",
                                device.getDeviceName(), device.getId(), populatedSlots);
                    }
                }

            } catch (Exception e) {
                log.error("BaselineService: failed to build baseline for device {} — {}",
                        device.getId(), e.getMessage());
            }
        }

        if (updated > 0 || newlyReady > 0) {
            log.info("BaselineService: updated {} device baselines, {} newly ready.", updated, newlyReady);
        }
    }

    /**
     * Get baseline data for a specific device — used by the detail page.
     * Returns a list of all 24 hour slots (some may be missing if no data).
     */
    public List<Map<String, Object>> getBaselineForDevice(Long deviceId) {
        List<DeviceBaseline> baselines = baselineRepository
                .findAllByDeviceIdOrderByHourOfDay(deviceId);

        return baselines.stream()
                .map(b -> {
                    Map<String, Object> slot = new LinkedHashMap<>();
                    slot.put("hour", b.getHourOfDay());
                    slot.put("avgBandwidth", b.getAvgBandwidth());
                    slot.put("stddevBandwidth", b.getStddevBandwidth());
                    slot.put("sampleCount", b.getSampleCount());
                    slot.put("upperBound", b.getAvgBandwidth() + 1.5 * b.getStddevBandwidth());
                    slot.put("lowerBound", Math.max(0, b.getAvgBandwidth() - 1.5 * b.getStddevBandwidth()));
                    return slot;
                })
                .collect(Collectors.toList());
    }

    /**
     * Compute sample standard deviation for a list of usage logs.
     * Uses the corrected sample stddev formula: sqrt(Σ(xi - mean)² / (n - 1))
     */
    private double computeStdDev(List<UsageLog> logs, double mean) {
        if (logs.size() < 2) return 0.0;

        double sumSquaredDiffs = logs.stream()
                .mapToDouble(log -> {
                    double diff = log.getBandwidthPercentage() - mean;
                    return diff * diff;
                })
                .sum();

        return Math.sqrt(sumSquaredDiffs / (logs.size() - 1));
    }
}
