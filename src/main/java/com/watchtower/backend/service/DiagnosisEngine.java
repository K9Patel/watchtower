package com.watchtower.backend.service;

import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.UsageLog;
import com.watchtower.backend.observer.AlertPublisher;
import com.watchtower.backend.repository.DeviceRepository;
import com.watchtower.backend.repository.UsageLogRepository;
import com.watchtower.backend.strategy.DiagnosisRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AJT — Strategy Pattern + Observer Pattern + @Scheduled (Unit 2 Threads):
 *
 * DiagnosisEngine runs every 30 seconds in a background thread.
 * For each active device it:
 *   1. Grabs last 60 seconds of UsageLogs
 *   2. Iterates all DiagnosisRule strategies (Strategy Pattern)
 *   3. If any rule fires → publishes the resulting Alert (Observer Pattern)
 *
 * All DiagnosisRule beans are injected as a List by Spring automatically.
 * All AlertListener beans are notified via AlertPublisher.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisEngine {

    private final DeviceRepository       deviceRepository;
    private final UsageLogRepository     usageLogRepository;
    private final List<DiagnosisRule>    rules;           // Strategy pattern — injected by Spring
    private final AlertPublisher         alertPublisher;  // Observer pattern — broadcasts alerts

    // AJT Unit 2 — Multithreading: runs in Spring's scheduler thread pool every 30s
    @Scheduled(fixedRate = 30_000)
    public void runDiagnosis() {
        List<Device> activeDevices = deviceRepository.findByIsActiveTrue();
        LocalDateTime since = LocalDateTime.now().minusSeconds(60);

        int alertsFired = 0;

        for (Device device : activeDevices) {
            List<UsageLog> recent = usageLogRepository
                    .findByDeviceAndTimestampAfter(device, since);

            if (recent.isEmpty()) continue;

            // Strategy Pattern: evaluate each rule against this device's logs
            for (DiagnosisRule rule : rules) {
                try {
                    rule.evaluate(device, recent).ifPresent(alert -> {
                        alertPublisher.publish(alert); // Observer Pattern
                    });
                    alertsFired++;
                } catch (Exception e) {
                    log.error("DiagnosisEngine: rule {} failed for {} — {}",
                            rule.getClass().getSimpleName(),
                            device.getDeviceName(),
                            e.getMessage());
                }
            }
        }

        log.debug("DiagnosisEngine: scanned {} devices, {} rule evaluations.",
                activeDevices.size(), alertsFired);
    }
}
