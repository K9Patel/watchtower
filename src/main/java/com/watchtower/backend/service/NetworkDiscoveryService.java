package com.watchtower.backend.service;

import com.watchtower.backend.entity.Device;
import com.watchtower.backend.repository.DeviceRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AJT Unit 3 — Java Networking:
 * Discovers real devices on the local WiFi/LAN network using ARP table.
 *
 * How it works:
 *   1. On startup: marks ALL stored devices OFFLINE (live network is the truth)
 *   2. Runs `arp -a` command on Windows to read the OS ARP cache
 *   3. Parses each line to extract IP address and MAC address
 *   4. Pings each IP with InetAddress.isReachable() to verify it's alive
 *   5. Auto-registers new devices / updates IPs for existing ones
 *   6. After each scan: marks devices NOT in ARP table as OFFLINE
 *   7. Purges devices offline for > 10 minutes (they left the network)
 *   8. Runs every 60 seconds to discover newly joined devices
 *
 * Only active when spring.profiles.active=real
 */
@Slf4j
@Service
@Profile("real")
@RequiredArgsConstructor
public class NetworkDiscoveryService {

    private final DeviceRepository deviceRepository;

    // How long a device can be absent before it's deleted (10 minutes)
    private static final int PURGE_AFTER_MINUTES = 10;

    // Regex to parse Windows `arp -a` output lines like:
    //   192.168.1.1           aa-bb-cc-dd-ee-ff     dynamic
    private static final Pattern ARP_LINE_PATTERN = Pattern.compile(
            "^\\s*(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\s+" +
            "([0-9a-fA-F]{2}[:-][0-9a-fA-F]{2}[:-][0-9a-fA-F]{2}[:-]" +
            "[0-9a-fA-F]{2}[:-][0-9a-fA-F]{2}[:-][0-9a-fA-F]{2})\\s+" +
            "(\\S+)"
    );

    /**
     * On app startup: reset all stored devices to OFFLINE.
     * The next scheduled scan will mark reachable ones back to ONLINE.
     * This ensures we never show stale data from a previous network session.
     */
    @PostConstruct
    public void resetOnStartup() {
        long count = deviceRepository.count();
        if (count > 0) {
            deviceRepository.markAllOffline();
            log.info("NetworkDiscovery: startup reset — marked {} stored devices OFFLINE. " +
                     "Live scan will rebuild truth.", count);
        }
    }

    /**
     * AJT Unit 2 — Multithreading:
     * Runs in a background scheduled thread every 60 seconds.
     * Discovers devices on the local network and reconciles the database.
     */
    @Scheduled(fixedRate = 60000, initialDelay = 5000)
    public void discoverDevices() {
        log.info("NetworkDiscovery: scanning local network for devices...");

        try {
            // Step 1: Ping subnet to populate ARP cache
            pingSubnet();

            // Step 2: Read ARP table
            List<ArpEntry> arpEntries = readArpTable();

            if (arpEntries.isEmpty()) {
                log.warn("NetworkDiscovery: no ARP entries found. Network may not be connected.");
                return;
            }

            // Step 3: Process each ARP entry — add/update devices seen in this scan
            int newDevices = 0;
            int updatedDevices = 0;
            LocalDateTime now = LocalDateTime.now();

            for (ArpEntry entry : arpEntries) {
                if (isBroadcastOrMulticast(entry.mac)) continue;

                Optional<Device> existingByMac = deviceRepository.findByMacAddress(entry.mac);
                if (existingByMac.isPresent()) {
                    Device device = existingByMac.get();

                    // Update IP if it changed (DHCP reassignment)
                    if (!device.getIpAddress().equals(entry.ip)) {
                        device.setIpAddress(entry.ip);
                        log.info("NetworkDiscovery: updated IP for {} -> {}", device.getDeviceName(), entry.ip);
                        updatedDevices++;
                    }

                    // Re-ping to get fresh status
                    boolean reachable = isReachable(entry.ip);
                    device.setStatus(reachable ? "ONLINE" : "OFFLINE");

                    // Update lastSeenAt — this is the heartbeat that proves it's on THIS network
                    device.setLastSeenAt(now);
                    deviceRepository.save(device);
                    continue;
                }

                // Check by IP as fallback
                Optional<Device> existingByIp = deviceRepository.findByIpAddress(entry.ip);
                if (existingByIp.isPresent()) {
                    Device device = existingByIp.get();
                    device.setLastSeenAt(now);
                    boolean reachable = isReachable(entry.ip);
                    device.setStatus(reachable ? "ONLINE" : "OFFLINE");
                    deviceRepository.save(device);
                    continue;
                }

                // New device on network — register it
                boolean reachable = isReachable(entry.ip);
                String hostname = resolveHostname(entry.ip);

                Device newDevice = Device.builder()
                        .deviceName(hostname)
                        .ipAddress(entry.ip)
                        .macAddress(entry.mac)
                        .status(reachable ? "ONLINE" : "OFFLINE")
                        .isActive(true)
                        .lastSeenAt(now)
                        .build();

                deviceRepository.save(newDevice);
                newDevices++;
                log.info("NetworkDiscovery: found new device {} ({}) [{}] — {}",
                        hostname, entry.ip, entry.mac, reachable ? "ONLINE" : "OFFLINE");
            }

            // ── Step 4: THE KEY FIX ───────────────────────────────────────────
            // Any device in the DB whose lastSeenAt was NOT updated this scan
            // was NOT present in the ARP table → it left the network.
            // Mark it OFFLINE immediately.
            // ─────────────────────────────────────────────────────────────────
            LocalDateTime scanStart = now.minusSeconds(30); // grace period for scan duration
            List<Device> unseenDevices = deviceRepository.findByLastSeenAtBefore(scanStart);
            int markedOffline = 0;
            for (Device device : unseenDevices) {
                // Skip "My Laptop" — RealDataService manages it separately
                if ("My Laptop".equals(device.getDeviceName())) continue;
                if ("ONLINE".equals(device.getStatus())) {
                    device.setStatus("OFFLINE");
                    deviceRepository.save(device);
                    markedOffline++;
                    log.info("NetworkDiscovery: {} ({}) not seen in scan — marked OFFLINE",
                            device.getDeviceName(), device.getIpAddress());
                }
            }

            // ── Step 5: PURGE stale devices ───────────────────────────────────
            // Devices that have been OFFLINE for > PURGE_AFTER_MINUTES are truly gone.
            // Delete them so the UI shows only what's on the current network.
            // ─────────────────────────────────────────────────────────────────
            LocalDateTime purgeCutoff = LocalDateTime.now().minusMinutes(PURGE_AFTER_MINUTES);
            deviceRepository.deleteStaleOfflineDevices(purgeCutoff);

            long totalDevices = deviceRepository.count();
            log.info("NetworkDiscovery: scan complete. {} new, {} updated, {} marked offline, {} total devices.",
                    newDevices, updatedDevices, markedOffline, totalDevices);

        } catch (Exception e) {
            log.error("NetworkDiscovery: scan failed — {}", e.getMessage(), e);
        }
    }

    /**
     * Pings a range of IPs in the local subnet to populate the ARP cache.
     * Uses quick parallel pings (1 second timeout each).
     */
    private void pingSubnet() {
        try {
            String localIp = InetAddress.getLocalHost().getHostAddress();
            String subnet = localIp.substring(0, localIp.lastIndexOf('.'));

            log.debug("NetworkDiscovery: pinging subnet {}.1-254 to populate ARP cache...", subnet);

            // AJT Unit 2 — Multithreading: ping multiple IPs in parallel for speed
            List<Thread> threads = new ArrayList<>();
            for (int i = 1; i <= 254; i++) {
                final String ip = subnet + "." + i;
                Thread t = new Thread(() -> {
                    try { InetAddress.getByName(ip).isReachable(1000); }
                    catch (Exception ignored) {}
                });
                t.setDaemon(true);
                threads.add(t);
                t.start();
            }

            for (Thread t : threads) {
                try { t.join(3000); } catch (InterruptedException ignored) {}
            }

            log.debug("NetworkDiscovery: subnet ping sweep complete.");

        } catch (Exception e) {
            log.warn("NetworkDiscovery: subnet ping failed — {}", e.getMessage());
        }
    }

    /**
     * AJT Unit 3 — Java Networking / Process execution:
     * Reads the OS ARP table by running `arp -a` on Windows.
     */
    private List<ArpEntry> readArpTable() throws Exception {
        List<ArpEntry> entries = new ArrayList<>();

        String os = System.getProperty("os.name").toLowerCase();
        String flag = os.contains("win") ? "-a" : "-n";

        ProcessBuilder pb = new ProcessBuilder("arp", flag);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());

        for (String line : output.split("\n")) {
            Matcher matcher = ARP_LINE_PATTERN.matcher(line.trim());
            if (matcher.find()) {
                String ip   = matcher.group(1);
                String mac  = matcher.group(2).replace('-', ':').toUpperCase();
                String type = matcher.group(3);

                // Only keep "dynamic" entries — static ones are usually virtual/system
                if (type.equalsIgnoreCase("dynamic") || !os.contains("win")) {
                    entries.add(new ArpEntry(ip, mac));
                }
            }
        }

        log.debug("NetworkDiscovery: parsed {} ARP entries.", entries.size());
        return entries;
    }

    /** AJT Unit 3 — InetAddress: checks if a host is reachable on the network */
    private boolean isReachable(String ip) {
        try {
            return InetAddress.getByName(ip).isReachable(1500);
        } catch (Exception e) {
            return false;
        }
    }

    /** AJT Unit 3 — InetAddress: reverse DNS lookup to resolve hostname */
    private String resolveHostname(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            String hostname = addr.getCanonicalHostName();
            if (hostname.equals(ip)) {
                return "Device-" + ip.substring(ip.lastIndexOf('.') + 1);
            }
            return hostname;
        } catch (Exception e) {
            return "Device-" + ip.substring(ip.lastIndexOf('.') + 1);
        }
    }


    private boolean isBroadcastOrMulticast(String mac) {
        return mac.equalsIgnoreCase("FF:FF:FF:FF:FF:FF") ||
               mac.startsWith("01:00:5E") ||  // IPv4 multicast
               mac.startsWith("33:33");        // IPv6 multicast
    }

    /** Simple record to hold parsed ARP table entries. */
    private record ArpEntry(String ip, String mac) {}
}
