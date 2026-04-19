package com.watchtower.backend.config;

import com.watchtower.backend.entity.Device;
import com.watchtower.backend.repository.AlertRepository;
import com.watchtower.backend.repository.DeviceRepository;
import com.watchtower.backend.repository.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * WatchTower Startup Initializer
 *
 * Runs on every application start to clean up stale data and ensure
 * the database reflects only real, live network state.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class RealModeInitializer implements CommandLineRunner {

    private final DeviceRepository    deviceRepository;
    private final UsageLogRepository  usageLogRepository;
    private final AlertRepository     alertRepository;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("RealModeInitializer: Starting clean-up for fresh live network state...");

        // 1. Wipe all old alerts — fresh start for diagnosis engine
        alertRepository.deleteAll();
        
        // 2. Preserve usage logs so per-user scoped history can continue across sessions.

        // 3. Remove stale/legacy devices that shouldn't persist across restarts
        List<Device> staleDevices = deviceRepository.findAll().stream()
                .filter(d -> {
                    String name = d.getDeviceName() != null ? d.getDeviceName().toLowerCase() : "";
                    String ip = d.getIpAddress() != null ? d.getIpAddress() : "";
                    
                    // Remove old simulated devices and the hardcoded "My Laptop" entry
                    // (RealDataService will recreate "My Laptop" with correct data on first tick)
                    boolean oldSimulated = name.equals("my laptop") ||
                           name.startsWith("device-") || 
                           name.equals("dell") || 
                           name.startsWith("student-") || 
                           name.startsWith("staff-");
                    
                    // Remove old 192.168.1.x devices (from previous network before hotspot switch)
                    boolean oldNetwork = ip.startsWith("192.168.1.");
                    
                    return oldSimulated || oldNetwork;
                })
                .toList();

        if (!staleDevices.isEmpty()) {
            log.info("RealModeInitializer: Removing {} stale devices: {}", 
                staleDevices.size(),
                staleDevices.stream().map(Device::getDeviceName).toList());
            deviceRepository.deleteAll(staleDevices);
        }

        // 4. DELETE all auto-discovered devices on startup
        // Reason: We can't know if the network changed while the app was offline.
        // Auto-discovered devices will be re-discovered in the first scan.
        // This ensures no stale devices from previous networks linger.
        List<Device> autoDiscoveredDevices = deviceRepository.findByIsAutoDiscoveredTrue();
        if (!autoDiscoveredDevices.isEmpty()) {
            log.info("RealModeInitializer: Deleting {} auto-discovered devices from previous session", 
                autoDiscoveredDevices.size());
            deviceRepository.deleteAll(autoDiscoveredDevices);
        }

        log.info("RealModeInitializer: Clean-up complete. Database is ready for live network monitoring.");
    }
}
