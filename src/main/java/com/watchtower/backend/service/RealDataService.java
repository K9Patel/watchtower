package com.watchtower.backend.service;

import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.UsageLog;
import com.watchtower.backend.repository.DeviceRepository;
import com.watchtower.backend.repository.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.NetworkInterface;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealDataService {

    private final DeviceRepository deviceRepository;
    private final UsageLogRepository usageLogRepository;
    private final AnalysisService analysisService;
    private final DashboardWebSocketService webSocketService;
    private final MacVendorService macVendorService;
    private final PacketCaptureService packetCaptureService;
    private final NetworkHealthService networkHealthService;

    // tracks previous reading so we can calculate delta bytes
    private long lastBytesIn  = 0;
    private long lastBytesOut = 0;
    private boolean firstRun  = true;

    private String actualMac = "Unknown";

    @PostConstruct
    public void init() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            NetworkInterface ni = NetworkInterface.getByInetAddress(localHost);
            if (ni != null) {
                byte[] mac = ni.getHardwareAddress();
                if (mac != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < mac.length; i++) {
                        sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? ":" : "")).toString();
                    }
                    actualMac = sb.toString();
                }
            }
        } catch (Exception e) {
            log.warn("Could not determine actual MAC address", e);
        }
    }

    // AJT Unit 2 — Multithreading: background scheduled task pattern
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

            // find or create "My Laptop" device for this machine's own traffic
            Device myDevice = deviceRepository.findByDeviceName("My Laptop")
                .map(d -> {
                    // Update MAC address if it was REAL-NIC or missing
                    if ("REAL-NIC".equals(d.getMacAddress()) || d.getMacAddress() == null) {
                        d.setMacAddress(actualMac);
                    }
                    // Populate vendor name if missing
                    if (d.getVendorName() == null || "Unknown".equals(d.getVendorName())) {
                        String vendor = macVendorService.lookup(actualMac);
                        if (!"Unknown".equals(vendor)) {
                            d.setVendorName(vendor);
                        }
                    }
                    d.setIpAddress(localIp);
                    d.setStatus("ONLINE");
                    d.setLastSeenAt(LocalDateTime.now());
                    deviceRepository.save(d);
                    return d;
                })
                .orElseGet(() -> {
                    String vendor = macVendorService.lookup(actualMac);
                    return deviceRepository.save(Device.builder()
                        .deviceName("My Laptop")
                        .ipAddress(localIp)
                        .macAddress(actualMac)
                        .vendorName("Unknown".equals(vendor) ? null : vendor)
                        .build());
                });

            usageLogRepository.save(UsageLog.builder()
                .device(myDevice)
                .bytesUsed(totalMB)
                .bandwidthPercentage(pct)
                .trafficType(classifyTrafficType(totalMB, null, true))
                .timestamp(LocalDateTime.now())
                .build());

            log.info("RealDataService: recorded {} MB (in: {} bytes, out: {} bytes)",
                     String.format("%.2f", totalMB), deltaIn, deltaOut);

            // Also ping all discovered devices and record basic usage logs
            recordDiscoveredDeviceActivity();

            // Recalculate health after fresh usage data is persisted for this cycle.
            networkHealthService.recalculateNow(false);

            // Push real-time update to React frontend
            webSocketService.pushDashboardUpdate(analysisService.getFullSummary());

        } catch (Exception e) {
            log.error("RealDataService error: {}", e.getMessage());
        }
    }

    /**
     * AJT Unit 3 — Java Networking:
     * For every discovered device in the database, pings it and records an activity log.
     * This ensures real-mode dashboards show data for ALL WiFi devices,
     * not just "My Laptop".
     *
     * Note: We can't read actual per-device byte counts without router SNMP access,
     * so we record ping latency as a proxy for device activity.
     */
    private void recordDiscoveredDeviceActivity() {
        List<Device> allDevices = deviceRepository.findByIsActiveTrue();
        LocalDateTime now = LocalDateTime.now();

        for (Device device : allDevices) {
            // skip "My Laptop" — already recorded above with real byte counts
            if ("My Laptop".equals(device.getDeviceName())) continue;

            try {
                InetAddress addr = InetAddress.getByName(device.getIpAddress());

                long start = System.currentTimeMillis();
                boolean reachable = addr.isReachable(2000);
                long latencyMs = System.currentTimeMillis() - start;

                // Update device status based on ping result
                String newStatus = reachable ? "ONLINE" : "OFFLINE";
                if (reachable) {
                    device.setLastSeenAt(now);
                }
                if (!newStatus.equals(device.getStatus())) {
                    device.setStatus(newStatus);
                    deviceRepository.save(device);
                } else if (reachable) {
                    // Persist last_seen_at refresh even when status does not change.
                    deviceRepository.save(device);
                }

                // Prefer true packet-capture bytes when available; otherwise fallback estimator.
                double measuredMB;
                if (packetCaptureService.isCaptureAvailable()) {
                    measuredMB = packetCaptureService.consumeMegabytesForIp(device.getIpAddress());
                } else {
                    measuredMB = reachable ? Math.max(0.01, latencyMs * 0.005) : 0.0;
                }
                double measuredPct = Math.min((measuredMB / 100.0) * 100, 100.0);

                usageLogRepository.save(UsageLog.builder()
                    .device(device)
                    .bytesUsed(measuredMB)
                    .bandwidthPercentage(measuredPct)
                    .trafficType(classifyTrafficType(measuredMB, latencyMs, reachable))
                    .timestamp(now)
                    .build());

            } catch (Exception e) {
                log.debug("RealDataService: couldn't ping {} — {}", device.getDeviceName(), e.getMessage());

                // Keep 10-second cadence even on ping failure.
                usageLogRepository.save(UsageLog.builder()
                    .device(device)
                    .bytesUsed(0.0)
                    .bandwidthPercentage(0.0)
                    .trafficType(classifyTrafficType(0.0, null, false))
                    .timestamp(now)
                    .build());
            }
        }
    }

    private String classifyTrafficType(double mbInWindow, Long latencyMs, boolean reachable) {
        if (!reachable || mbInWindow <= 0.0) {
            return "BROWSING";
        }

        if (mbInWindow >= 12.0) {
            return "DOWNLOAD";
        }

        if (mbInWindow >= 4.0 && latencyMs != null && latencyMs <= 150) {
            return "STREAMING";
        }

        if (latencyMs != null && latencyMs <= 80 && mbInWindow >= 1.0) {
            return "GAMING";
        }

        return "BROWSING";
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
            ProcessBuilder pb = new ProcessBuilder("netstat", "-e");
            pb.redirectErrorStream(true);
            Process process = pb.start();
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