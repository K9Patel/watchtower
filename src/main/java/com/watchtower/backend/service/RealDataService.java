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

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Enumeration;

@Slf4j
@Service
@Profile("real")    // AJT: only loads when spring.profiles.active=real
@RequiredArgsConstructor
public class RealDataService {

    private final DeviceRepository deviceRepository;
    private final UsageLogRepository usageLogRepository;

    // tracks previous reading so we can calculate delta bytes
    private long lastBytesIn  = 0;
    private long lastBytesOut = 0;
    private boolean firstRun  = true;

    // AJT Unit 2 — Multithreading: same @Scheduled pattern as SimulatorService
    // AJT Unit 3 — Java Networking: reads real bytes from this machine's NIC
    @Scheduled(fixedRate = 10000)
    public void collectRealData() {
        try {
            long[] current = getRealNetworkBytes();
            long currentIn  = current[0];
            long currentOut = current[1];

            // skip first run — no delta yet
            if (firstRun) {
                lastBytesIn  = currentIn;
                lastBytesOut = currentOut;
                firstRun = false;
                log.info("RealDataService: baseline captured. Next cycle will record real traffic.");
                return;
            }

            long deltaIn  = Math.max(0, currentIn  - lastBytesIn);
            long deltaOut = Math.max(0, currentOut - lastBytesOut);
            lastBytesIn  = currentIn;
            lastBytesOut = currentOut;

            double totalMB = (deltaIn + deltaOut) / (1024.0 * 1024.0);
            double pct     = Math.min((totalMB / 1000.0) * 100, 100.0);

            // AJT Unit 3 — InetAddress: get local machine IP
            String localIp = InetAddress.getLocalHost().getHostAddress();

            // find or create "My Laptop" device
            Device myDevice = deviceRepository.findByDeviceName("My Laptop")
                .orElseGet(() -> deviceRepository.save(Device.builder()
                    .deviceName("My Laptop")
                    .ipAddress(localIp)
                    .macAddress("REAL-NIC")
                    .deviceType(Device.DeviceType.ADMIN)
                    .build()));

            usageLogRepository.save(UsageLog.builder()
                .device(myDevice)
                .bytesUsed(totalMB)
                .bandwidthPercentage(pct)
                .trafficType("REAL_TRAFFIC")
                .timestamp(LocalDateTime.now())
                .build());

            log.info("RealDataService: recorded {:.2f} MB (in: {} bytes, out: {} bytes)",
                     totalMB, deltaIn, deltaOut);

        } catch (Exception e) {
            log.error("RealDataService error: {}", e.getMessage());
        }
    }

    // AJT Unit 3 — Java Networking:
    // NetworkInterface.getNetworkInterfaces() enumerates ALL network adapters.
    // We find the active non-loopback interface and read its byte counters.
    // In a real college deployment this runs on every machine and reports centrally.
    private long[] getRealNetworkBytes() throws Exception {
        long totalBytesIn  = 0;
        long totalBytesOut = 0;

        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            // Windows: use netstat -e to get total bytes sent/received
            Process process = Runtime.getRuntime().exec("netstat -e");
            String output = new String(process.getInputStream().readAllBytes());

            for (String line : output.split("\n")) {
                line = line.trim();
                if (line.startsWith("Bytes")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 3) {
                        try {
                            totalBytesIn  = Long.parseLong(parts[1].replace(",", ""));
                            totalBytesOut = Long.parseLong(parts[2].replace(",", ""));
                        } catch (NumberFormatException ignored) {}
                    }
                    break;
                }
            }
        } else {
            // Linux / Mac: enumerate NetworkInterfaces directly
            // AJT Unit 3: NetworkInterface class — reads hardware-level NIC statistics
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface nic : Collections.list(interfaces)) {
                // skip loopback (127.0.0.1) and inactive adapters
                if (nic.isLoopback() || !nic.isUp()) continue;

                // NetworkInterface doesn't expose byte counters directly on all JVMs
                // so we read from /proc/net/dev on Linux
                try {
                    String nicName = nic.getName();
                    String procFile = new String(
                        java.nio.file.Files.readAllBytes(
                            java.nio.file.Paths.get("/proc/net/dev")));
                    for (String line : procFile.split("\n")) {
                        if (line.trim().startsWith(nicName + ":")) {
                            String[] parts = line.trim().split("\\s+");
                            if (parts.length > 9) {
                                totalBytesIn  += Long.parseLong(parts[1]);
                                totalBytesOut += Long.parseLong(parts[9]);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        return new long[]{ totalBytesIn, totalBytesOut };
    }
}