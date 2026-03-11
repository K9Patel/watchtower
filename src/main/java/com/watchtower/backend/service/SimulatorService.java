package com.watchtower.backend.service;

import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.UsageLog;
import com.watchtower.backend.repository.DeviceRepository;
import com.watchtower.backend.repository.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
@Profile("simulator")   // AJT: only loads when spring.profiles.active=simulator
@RequiredArgsConstructor
public class SimulatorService {

    private final DeviceRepository deviceRepository;
    private final UsageLogRepository usageLogRepository;
    private final SimulationControlService controlService;

    private final Random random = new Random();

    private static final String[] TRAFFIC_TYPES = {
        "STREAMING", "BROWSING", "GAMING", "DOWNLOAD", "VOIP"
    };

    // AJT Unit 2 — Multithreading:
    // @Scheduled runs this method in a SEPARATE background thread every 10 seconds.
    // It is completely independent from the main HTTP request thread.
    // Both threads run simultaneously without blocking each other.
    // Spring manages the thread pool via @EnableScheduling in WatchtowerBackendApplication.
    @Scheduled(fixedRate = 10000)
    public void generateUsage() {

        // respect pause toggle from SimulationControlService
        if (!controlService.isRunning()) {
            log.debug("Simulator is paused — skipping this cycle.");
            return;
        }

        List<Device> activeDevices = deviceRepository.findByIsActiveTrue();

        if (activeDevices.isEmpty()) {
            log.warn("Simulator: no active devices found.");
            return;
        }

        int saved = 0;
        for (Device device : activeDevices) {

            // 15% chance of spike — simulates HD streaming or large download
            double mb;
            if (random.nextDouble() < 0.15) {
                mb = 200 + random.nextDouble() * 400;   // spike: 200–600 MB
            } else {
                mb = 1 + random.nextDouble() * 24;      // normal: 1–25 MB
            }

            String trafficType = TRAFFIC_TYPES[random.nextInt(TRAFFIC_TYPES.length)];
            double pct = Math.min((mb / 1000.0) * 100, 100.0);

            usageLogRepository.save(UsageLog.builder()
                .device(device)
                .bytesUsed(mb)
                .bandwidthPercentage(pct)
                .trafficType(trafficType)
                .timestamp(LocalDateTime.now())
                .build());

            saved++;
        }

        log.debug("Simulator: saved {} usage logs.", saved);
    }
}