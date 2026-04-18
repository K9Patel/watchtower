package com.watchtower.backend.config;

import com.watchtower.backend.entity.Device;
import com.watchtower.backend.repository.AlertRepository;
import com.watchtower.backend.repository.DeviceRepository;
import com.watchtower.backend.repository.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * WatchTower Real Network Mode — 100% Truth Protocol
 *
 * Enforces a strict pristine state by annihilating all legacy logs, 
 * orphaned ghost alerts, and simulated artifacts. 
 */
@Slf4j
@Component
@Order(1)
@Profile("real")
@RequiredArgsConstructor
public class RealModeInitializer implements CommandLineRunner {

    private final DeviceRepository    deviceRepository;
    private final UsageLogRepository  usageLogRepository;
    private final AlertRepository     alertRepository;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("RealModeInitializer: Engaging strict 100% Live Reset. Purging all historical databases...");

        // 1. Wipe absolutely all orphan alerts (including Z-Score anomalies for deleted fake devices)
        alertRepository.deleteAll();
        
        // 2. Wipe absolutely all legacy metric logs (this cleans out the 1400MB ghost bars in the charts)
        usageLogRepository.deleteAll();

        // 3. Purge any lingering simulated devices
        List<Device> fakeDevices = deviceRepository.findAll().stream()
                .filter(d -> {
                    String name = d.getDeviceName().toLowerCase();
                    return name.startsWith("device-") || name.equals("dell") || 
                           name.startsWith("student-") || name.startsWith("staff-");
                })
                .toList();

        if (!fakeDevices.isEmpty()) {
            deviceRepository.deleteAll(fakeDevices);
        }

        log.info("RealModeInitializer: Purgatory complete. Database is pristine. WatchTower is strictly streaming live hardware data going forward.");
    }
}
