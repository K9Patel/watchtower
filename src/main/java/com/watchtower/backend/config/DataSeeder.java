package com.watchtower.backend.config;

import com.watchtower.backend.entity.Device;
import com.watchtower.backend.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final DeviceRepository deviceRepository;

    @Override
    public void run(String... args) {

        if (deviceRepository.count() > 0) {
            log.info("DataSeeder: {} devices already exist — skipping.",
                     deviceRepository.count());
            return;
        }

        log.info("DataSeeder: Seeding 25 devices...");

        // 15 STUDENT devices
        for (int i = 1; i <= 15; i++) {
            deviceRepository.save(Device.builder()
                .deviceName("Student-Laptop-" + String.format("%02d", i))
                .ipAddress("192.168.1." + (100 + i))
                .macAddress(generateMac(i))
                .deviceType(Device.DeviceType.STUDENT)
                .build());
        }

        // 7 STAFF devices
        for (int i = 1; i <= 7; i++) {
            deviceRepository.save(Device.builder()
                .deviceName("Staff-PC-" + String.format("%02d", i))
                .ipAddress("192.168.2." + (10 + i))
                .macAddress(generateMac(20 + i))
                .deviceType(Device.DeviceType.STAFF)
                .build());
        }

        // 3 ADMIN devices
        for (int i = 1; i <= 3; i++) {
            deviceRepository.save(Device.builder()
                .deviceName("Admin-Server-" + String.format("%02d", i))
                .ipAddress("192.168.3." + (1 + i))
                .macAddress(generateMac(30 + i))
                .deviceType(Device.DeviceType.ADMIN)
                .build());
        }

        log.info("DataSeeder: Done — {} devices seeded.", deviceRepository.count());
    }

    private String generateMac(int seed) {
        return String.format("AA:BB:CC:DD:EE:%02X", seed % 256);
    }
}