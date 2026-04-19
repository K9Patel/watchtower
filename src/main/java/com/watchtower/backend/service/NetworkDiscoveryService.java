package com.watchtower.backend.service;

import com.watchtower.backend.entity.Device;
import com.watchtower.backend.repository.DeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live Network Auto-Discovery Service
 *
 * Core idea: the device table is a LIVE SNAPSHOT of what is on the network
 * RIGHT NOW — not a permanent registry. This service:
 *
 *   1. Runs every 60 seconds automatically (@Scheduled)
 *   2. Deletes auto-discovered devices not seen in the last 5 minutes (stale cleanup)
 *   3. Executes `arp -a` to get a fresh ARP table from the OS
 *   4. For each entry: update existing device OR register new one
 *   5. Stores the last ScanResult so the controller can serve it via API
 *
 * Manual scan trigger: POST /api/devices/scan → calls triggerScan()
 *
 * Windows ARP output format:
 *   Interface: 192.168.1.5 --- 0x8
 *     Internet Address      Physical Address      Type
 *     192.168.1.1           aa-bb-cc-dd-ee-ff     dynamic
 *     192.168.1.100         11-22-33-44-55-66     dynamic
 */
@Slf4j
@Service
public class NetworkDiscoveryService {

    // Stale timeout: auto-discovered devices not seen within 5 minutes are deleted
    private static final int STALE_AFTER_MINUTES = 5;

    // ARP output regex — matches: <IP>  <MAC>  <type>
    // Handles both Windows (aa-bb-cc-dd-ee-ff) and Linux (aa:bb:cc:dd:ee:ff) formats
    private static final Pattern ARP_PATTERN = Pattern.compile(
        "\\s*(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})" +   // group 1: IP
        "\\s+([0-9a-fA-F]{2}[:\\-][0-9a-fA-F]{2}[:\\-]" +      // group 2: MAC (start)
              "[0-9a-fA-F]{2}[:\\-][0-9a-fA-F]{2}[:\\-]" +
              "[0-9a-fA-F]{2}[:\\-][0-9a-fA-F]{2})" +
        "\\s+(\\S+)"                                             // group 3: type
    );

    // Ping timeout (milliseconds)
    private static final int PING_TIMEOUT_MS = 1500;

    private final DeviceRepository deviceRepository;
    private final DashboardWebSocketService webSocketService;
    private final MacVendorService macVendorService;
    private final ApplicationEventPublisher eventPublisher;

    // Stores the most recent scan result — served by GET /api/devices/scan/status
    private final AtomicReference<ScanResult> lastResult = new AtomicReference<>(null);

    // Track the previous network prefix to detect network changes
    private String previousNetworkPrefix = null;

    @Autowired
    public NetworkDiscoveryService(
            DeviceRepository deviceRepository,
            DashboardWebSocketService webSocketService,
            MacVendorService macVendorService,
            ApplicationEventPublisher eventPublisher) {
        this.deviceRepository = deviceRepository;
        this.webSocketService = webSocketService;
        this.macVendorService = macVendorService;
        this.eventPublisher = eventPublisher;
    }

    // ==========================================================================
    //  Scheduled Auto-Scan  (every 60 seconds)
    // ==========================================================================

    /**
     * AJT Unit 2 — Multithreading: @Scheduled runs this in Spring's task executor.
     * initialDelay=10s gives the app time to start before the first scan.
     */
    @Scheduled(fixedRate = 60_000, initialDelay = 10_000)
    public void scheduledScan() {
        log.info("NetworkDiscovery: starting scheduled scan...");
        ScanResult result = runScan();
        log.info("NetworkDiscovery: scan complete — found={}, new={}, updated={}, removedStale={}",
                result.totalFound(), result.newDevices(), result.updatedDevices(), result.removedStale());
    }

    // ==========================================================================
    //  Manual Trigger  (POST /api/devices/scan)
    // ==========================================================================

    /**
     * Called by DeviceController for the "Scan Now" button.
     * Runs in a background daemon thread so the HTTP response returns instantly.
     * The result will be available via GET /api/devices/scan/status.
     */
    public void triggerScan() {
        if (isScanRunning()) {
            log.info("NetworkDiscovery: manual trigger ignored — scan already running.");
            return;
        }
        Thread t = new Thread(() -> {
            log.info("NetworkDiscovery: manual scan triggered.");
            runScan();
        }, "manual-arp-scan");
        t.setDaemon(true);
        t.start();
    }

    /** Returns true if a scan is currently in progress */
    public boolean isScanRunning() {
        ScanResult r = lastResult.get();
        return r != null && r.scannedAt() == null; // null scannedAt = scan in progress sentinel
    }

    /** Returns the last scan result (may be null before first scan) */
    public ScanResult getLastResult() {
        return lastResult.get();
    }

    // ==========================================================================
    //  Core Scan Logic (ZERO FAKE DATA — Real Pings + DNS Only)
    // ==========================================================================

    private ScanResult runScan() {
        int removed = 0, newCount = 0, updatedCount = 0;
        List<Device> discovered = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        String currentNetworkPrefix = null;
        boolean networkChanged = false;

        try {
            // ── PART 2: Detect network change ────────────────────────────────
            currentNetworkPrefix = getCurrentNetworkPrefix();
            networkChanged = previousNetworkPrefix != null && !previousNetworkPrefix.equals(currentNetworkPrefix);
            
            if (networkChanged) {
                log.warn("NetworkDiscovery: ⚠ NETWORK CHANGED from {} to {}", previousNetworkPrefix, currentNetworkPrefix);
                log.info("NetworkDiscovery: Deleting all auto-discovered devices from old network...");
                
                // Delete all auto-discovered devices from the old network
                List<Device> oldDevices = deviceRepository.findByIsAutoDiscoveredTrue();
                for (Device d : oldDevices) {
                    deviceRepository.delete(d);
                }
                removed = oldDevices.size();
                
                // Push WebSocket network change event
                Map<String, Object> networkChangeEvent = Map.of(
                    "type", "NETWORK_CHANGED",
                    "previousNetwork", previousNetworkPrefix != null ? previousNetworkPrefix : "unknown",
                    "currentNetwork", currentNetworkPrefix,
                    "changedAt", now.toString(),
                    "message", "Network changed — rescanning devices"
                );
                webSocketService.pushMessage("/topic/network-change", networkChangeEvent);
                
                log.info("NetworkDiscovery: Network change event pushed. Cleared {} auto-discovered devices.", removed);
            }
            
            previousNetworkPrefix = currentNetworkPrefix;

            // ── Step 1: Delete stale auto-discovered devices ──────────────────
            // Devices not seen in the last 5 minutes have left the network.
            // This makes the table a live snapshot, not a permanent registry.
            LocalDateTime staleCutoff = now.minusMinutes(STALE_AFTER_MINUTES);
            int staleRemoved = deviceRepository.deleteStaleAutoDiscovered(staleCutoff);
            removed += staleRemoved;
            if (staleRemoved > 0) {
                log.info("NetworkDiscovery: removed {} stale auto-discovered device(s).", staleRemoved);
            }

            // ── Detect gateway IP (local machine) and exclude from device list
            String localIp = getLocalIpAddress();
            log.debug("NetworkDiscovery: Detected local IP = {}", localIp);

            // ── Step 2: Run `arp -a` to get OS ARP cache + active subnet scan ─
            List<ArpEntry> entries = readArpTable();
            log.debug("NetworkDiscovery: parsed {} ARP entries from `arp -a`.", entries.size());

            // Filter out gateway from ARP entries
            List<ArpEntry> filteredEntries = entries.stream()
                    .filter(entry -> !entry.ip().equals(localIp))
                    .collect(java.util.stream.Collectors.toList());
            
            if (!filteredEntries.isEmpty()) {
                log.info("NetworkDiscovery: Filtered {} entry(ies) — removed gateway {}",
                        entries.size() - filteredEntries.size(), localIp);
            }
            entries = filteredEntries;

            // Active subnet scan to find devices that haven't communicated yet
            List<String> respondingIps = activeSubnetScan(currentNetworkPrefix, localIp);
            List<ArpEntry> activeDiscovered = new ArrayList<>();
            for (String ip : respondingIps) {
                // Try to get MAC from existing ARP entries
                Optional<ArpEntry> arpMatch = entries.stream()
                        .filter(e -> e.ip().equals(ip))
                        .findFirst();
                
                if (arpMatch.isPresent()) {
                    // Already in ARP table, will be processed below
                    log.debug("NetworkDiscovery: {} already in ARP table", ip);
                } else {
                    // Responding but not in ARP yet — try to get MAC
                    log.info("NetworkDiscovery: ★ Found responding IP via active scan: {} (MAC unknown yet)", ip);
                    // Will likely get MAC in next ARP cycle, for now register as responding
                    activeDiscovered.add(new ArpEntry(ip, "00:00:00:00:00:00"));  // Placeholder MAC
                }
            }
            
            // Merge ARP and active scan results
            entries.addAll(activeDiscovered);

            if (entries.isEmpty()) {
                log.warn("NetworkDiscovery: no ARP entries or responding IPs found. Is the machine connected to a network?");
                ScanResult empty = new ScanResult(0, 0, 0, removed, now, List.of(), currentNetworkPrefix, networkChanged);
                lastResult.set(empty);
                return empty;
            }

            // ── PART 5 Step 3: Process each ARP entry + actual ping ──────────
            Set<String> arpMacs = new HashSet<>();
            
            for (ArpEntry entry : entries) {
                // Skip broadcast, incomplete, and multicast entries
                if (isInvalidEntry(entry)) continue;
                
                arpMacs.add(entry.mac());
                Optional<Device> existing = deviceRepository.findByMacAddress(entry.mac());

                // ── PART 1: Ping device to determine REAL online/offline status ──
                boolean isOnline = pingDevice(entry.ip());
                String status = isOnline ? "ONLINE" : "OFFLINE";
                
                // ── PART 3: Reverse DNS lookup for REAL hostname ──────────────
                String realHostname = reverseDnsLookup(entry.ip());
                // If DNS returns same as IP or fails, hostname is null
                if (realHostname != null && realHostname.equals(entry.ip())) {
                    realHostname = null;
                }

                if (existing.isPresent()) {
                    // ── KNOWN device: refresh status based on ping result
                    Device device = existing.get();
                    boolean changed = false;

                    if (!entry.ip().equals(device.getIpAddress())) {
                        device.setIpAddress(entry.ip());
                        changed = true;
                    }
                    
                    // Only update hostname if we got a real DNS result
                    if (realHostname != null && !realHostname.equals(device.getDeviceName())) {
                        device.setDeviceName(realHostname);
                        changed = true;
                    }
                    
                    // Populate vendor if missing
                    if (device.getVendorName() == null || "Unknown".equals(device.getVendorName())) {
                        String vendor = macVendorService.lookup(entry.mac());
                        if (vendor != null && !"Unknown".equals(vendor)) {
                            device.setVendorName(vendor);
                            changed = true;
                        }
                    }
                    
                    if (!status.equals(device.getStatus())) {
                        device.setStatus(status);
                        changed = true;
                    }

                    // Keep lastSeenAt as "last time we actually saw this device alive".
                    // Do not move it forward on ping timeout.
                    if (isOnline) {
                        device.setLastSeenAt(now);
                    }
                    device.setIsActive(true);
                    deviceRepository.save(device);
                    webSocketService.pushDeviceUpdate(device);
                    discovered.add(device);
                    if (changed) updatedCount++;

                } else {
                    // ── NEW device: register with REAL hostname (or null)
                    String vendor = macVendorService.lookup(entry.mac());
                    
                    // Use real hostname if available, otherwise use IP address
                    String deviceName = realHostname != null ? realHostname : entry.ip();
                    
                    // Safety check: verify device doesn't already exist (race condition protection)
                    Optional<Device> doubleCheck = deviceRepository.findByMacAddress(entry.mac());
                    if (doubleCheck.isPresent()) {
                        log.warn("NetworkDiscovery: ⚠ Race condition detected — device {} already exists, skipping duplicate creation", entry.mac());
                        discovered.add(doubleCheck.get());
                        continue;
                    }
                    
                    Device newDevice = Device.builder()
                            .deviceName(deviceName)
                            .ipAddress(entry.ip())
                            .macAddress(entry.mac())
                            .vendorName(vendor != null && !"Unknown".equals(vendor) ? vendor : null)
                            .status(status)
                            .isActive(true)
                            .isAutoDiscovered(true)
                            .lastSeenAt(isOnline ? now : null)
                            .registeredAt(now)
                            .build();

                    deviceRepository.save(newDevice);
                    webSocketService.pushDeviceUpdate(newDevice);
                    discovered.add(newDevice);
                    newCount++;
                    
                    log.info("NetworkDiscovery: ★ NEW device — hostname={} ip={} mac={} status={} vendor={}",
                            deviceName, entry.ip(), entry.mac(), status, vendor);
                }
            }
            
            // ── PART 5 Step 4: Find devices NOT in this ARP scan ──────────────
            // These devices have left the network
            List<Device> allDevices = deviceRepository.findAll();
            for (Device device : allDevices) {
                if (!arpMacs.contains(device.getMacAddress())) {
                    if (Boolean.TRUE.equals(device.getIsAutoDiscovered())) {
                        // Auto-discovered device has left network → delete it
                        deviceRepository.delete(device);
                        removed++;
                        log.info("NetworkDiscovery: Removed device {} (MAC {}) — left network", 
                                device.getDeviceName(), device.getMacAddress());
                    } else {
                        // Manually managed device: avoid false OFFLINE flips caused by ARP gaps.
                        // Keep ONLINE if it is this machine or it was seen recently.
                        boolean isLocalDevice = localIp != null && localIp.equals(device.getIpAddress());
                        boolean seenRecently = device.getLastSeenAt() != null
                                && device.getLastSeenAt().isAfter(now.minusSeconds(45));

                        if (isLocalDevice || seenRecently) {
                            continue;
                        }

                        if (!"OFFLINE".equals(device.getStatus())) {
                            device.setStatus("OFFLINE");
                            deviceRepository.save(device);
                            webSocketService.pushDeviceUpdate(device);
                        }
                    }
                }
            }
            
            // ── PART 5 Step 5: Clean up stale IP entries
            // If a device changed IPs via DHCP, old entries at the old IP should be removed
            // (especially auto-discovered devices, which are temporary)
            for (ArpEntry entry : entries) {
                // Find any OTHER devices (different MAC) with this same IP
                List<Device> conflictingDevices = deviceRepository.findAllByIpAddress(entry.ip());
                for (Device conflict : conflictingDevices) {
                    if (!conflict.getMacAddress().equals(entry.mac())) {
                        // Different MAC, same IP → the IP was reassigned, old device is stale
                        if (Boolean.TRUE.equals(conflict.getIsAutoDiscovered())) {
                            log.info("NetworkDiscovery: Cleaning up stale entry — {} at {} (now on {}) ",
                                    conflict.getDeviceName(), entry.ip(), entry.mac());
                            deviceRepository.delete(conflict);
                            removed++;
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("NetworkDiscovery: scan failed — {}", e.getMessage(), e);
        }

        ScanResult result = new ScanResult(
                discovered.size(), newCount, updatedCount, removed,
                now, discovered, currentNetworkPrefix, networkChanged
        );
        lastResult.set(result);
        return result;
    }

    // ==========================================================================
    //  PART 1: Ping Detection (Real Online/Offline Status)
    // ==========================================================================

    /**
     * Perfectly real ONLINE/OFFLINE detection using InetAddress.isReachable().
     * Returns true if the device responds to ping (ICMP) within timeout.
     * Returns false if no response or any error (timeout treated as OFFLINE).
     */
    private boolean pingDevice(String ipAddress) {
        try {
            InetAddress address = InetAddress.getByName(ipAddress);
            return address.isReachable(PING_TIMEOUT_MS);
        } catch (Exception e) {
            log.debug("NetworkDiscovery: ping failed for {} — treating as OFFLINE. Error: {}", ipAddress, e.getMessage());
            return false;  // No response = OFFLINE
        }
    }

    // ==========================================================================
    //  PART 2: Network Change Detection
    // ==========================================================================

    /**
     * Gets the current network prefix (first 3 octets) from the active network interface.
     * Example: "192.168.1" (from 192.168.1.5)
     * Used to detect when the machine switches WiFi networks.
     */
    private String getCurrentNetworkPrefix() {
        try {
            NetworkInterface ni;
            java.util.Enumeration<NetworkInterface> interfaces = 
                NetworkInterface.getNetworkInterfaces();
            
            while (interfaces.hasMoreElements()) {
                ni = interfaces.nextElement();
                
                // Skip loopback and inactive interfaces
                if (ni.isLoopback() || !ni.isUp()) continue;
                
                // Find first active IPv4 address
                java.util.Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    
                    // Only use IPv4 addresses
                    if (addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        // Extract first 3 octets
                        String[] parts = ip.split("\\.");
                        if (parts.length == 4) {
                            String prefix = parts[0] + "." + parts[1] + "." + parts[2];
                            log.debug("NetworkDiscovery: current network prefix = {}", prefix);
                            return prefix;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("NetworkDiscovery: Failed to get network prefix — {}", e.getMessage());
        }
        return "unknown";
    }

    // ==========================================================================
    //  PART 3: Reverse DNS Lookup (Real Hostnames Only)
    // ==========================================================================

    /**
     * Performs a reverse DNS lookup to find the real hostname for an IP address.
     * Returns the hostname if found, returns the IP address if DNS has no record,
     * or null if we need to treat it as unknown.
     */
    private String reverseDnsLookup(String ipAddress) {
        try {
            InetAddress addr = InetAddress.getByName(ipAddress);
            String hostname = addr.getCanonicalHostName();
            
            // If the hostname returned is the same as the IP, DNS has no record
            if (hostname.equals(ipAddress)) {
                log.debug("NetworkDiscovery: DNS lookup for {} has no record (returned same IP)", ipAddress);
                return null;  // No DNS record — store as null, display as IP in frontend
            }
            
            log.debug("NetworkDiscovery: DNS lookup for {} → {}", ipAddress, hostname);
            return hostname;
            
        } catch (UnknownHostException e) {
            log.debug("NetworkDiscovery: DNS lookup failed for {} — {}", ipAddress, e.getMessage());
            return null;  // Failed to resolve
        }
    }

    // ==========================================================================
    //  Gateway Detection
    // ==========================================================================

    /**
     * Gets the local machine's IP address (the gateway in hotspot mode).
     * Returns the first active IPv4 address found on non-loopback interfaces.
     */
    private String getLocalIpAddress() {
        try {
            java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                
                java.util.Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("NetworkDiscovery: Failed to get local IP — {}", e.getMessage());
        }
        return null;
    }

    // ==========================================================================
    //  Active Subnet Scanning (Find devices that haven't communicated yet)
    // ==========================================================================

    /**
     * Actively scans the subnet by pinging all IPs (except gateway).
     * Devices that respond are reachable but may not be in ARP table yet.
     * This finds newly connected devices (e.g., phone that just joined WiFi).
     *
     * Returns list of responding IPs. MAC addresses fetched via ARP on responders.
     */
    private List<String> activeSubnetScan(String networkPrefix, String gatewayIp) {
        List<String> respondingIps = java.util.Collections.synchronizedList(new ArrayList<>());
        String[] prefixParts = networkPrefix.split("\\.");
        
        if (prefixParts.length != 3) {
            log.warn("NetworkDiscovery: Invalid network prefix format: {}", networkPrefix);
            return respondingIps;
        }

        log.info("NetworkDiscovery: Starting active subnet scan for {}. * (excluding gateway {})", networkPrefix, gatewayIp);
        
        // Scan IPs from .1 to .254 (parallel for speed)
        java.util.stream.IntStream.rangeClosed(1, 254).parallel().forEach(i -> {
            String ip = networkPrefix + "." + i;
            
            // Skip gateway IP
            if (ip.equals(gatewayIp)) {
                return;
            }
            
            try {
                InetAddress addr = InetAddress.getByName(ip);
                if (addr.isReachable(500)) {  // Shorter timeout for speed
                    respondingIps.add(ip);
                    log.debug("NetworkDiscovery: Active scan found responding IP: {}", ip);
                }
            } catch (Exception e) {
                // Timeout or unreachable — skip
            }
        });
        
        log.info("NetworkDiscovery: Active subnet scan complete — found {} responding IP(s)", respondingIps.size());
        return respondingIps;
    }

    // ==========================================================================
    //  ARP Table Reader
    // ==========================================================================

    /**
     * AJT Unit 3 — Java Networking / Process execution:
     * Runs `arp -a` on Windows and parses every line for IP+MAC pairs.
     */
    private List<ArpEntry> readArpTable() throws Exception {
        List<ArpEntry> entries = new ArrayList<>();

        // Works on Windows (`arp -a`) and Linux/Mac (`arp -n`)
        String os   = System.getProperty("os.name", "").toLowerCase();
        String flag = os.contains("win") ? "-a" : "-n";

        Process process = Runtime.getRuntime().exec(new String[]{"arp", flag});
        String output   = new String(process.getInputStream().readAllBytes());
        process.waitFor();

        for (String line : output.split("\\r?\\n")) {
            Matcher m = ARP_PATTERN.matcher(line);
            if (!m.find()) continue;

            String ip   = m.group(1).trim();
            String mac  = normalizeMac(m.group(2).trim());
            String type = m.group(3).trim().toLowerCase();

            // On Windows, only "dynamic" entries are real devices.
            // On Linux, skip "incomplete" entries (no MAC resolved yet).
            if (os.contains("win") && !type.equals("dynamic")) continue;
            if (type.equals("incomplete"))                      continue;

            entries.add(new ArpEntry(ip, mac));
        }

        return entries;
    }

    // ==========================================================================
    //  Helpers
    // ==========================================================================

    /**
     * Normalises any MAC format to lowercase colon-separated: aa:bb:cc:dd:ee:ff
     */
    private String normalizeMac(String raw) {
        return raw.replace("-", ":").toLowerCase();
    }

    /**
     * Returns false for entries we should ignore:
     *   - Broadcast: 255.255.255.255 or ff:ff:ff:ff:ff:ff
     *   - IPv4 multicast MACs: 01:00:5e:xx:xx:xx
     *   - IPv6 multicast MACs: 33:33:xx:xx:xx:xx
     *   - All-zeros (ARP not yet resolved): 00:00:00:00:00:00
     */
    private boolean isInvalidEntry(ArpEntry entry) {
        String ip  = entry.ip();
        String mac = entry.mac();

        if (ip.endsWith(".255"))                   return true;  // broadcast
        if ("ff:ff:ff:ff:ff:ff".equals(mac))       return true;  // Ethernet broadcast
        if ("00:00:00:00:00:00".equals(mac))       return true;  // unresolved
        if (mac.startsWith("01:00:5e"))            return true;  // IPv4 multicast
        if (mac.startsWith("33:33"))               return true;  // IPv6 multicast
        if (ip.startsWith("224.") || ip.startsWith("239.")) return true; // multicast IPs

        return false;
    }

    /**
     * Builds a human-readable device name from the MAC address last 4 hex digits.
     * Example: MAC "aa:bb:cc:dd:ee:ff" → "Device-eeff"
     *
     * NOTE: This method is DEPRECATED — we now use REAL hostnames from DNS
     * or the IP address if no DNS record exists. Never generate fake names.
     * Kept for reference only.
     */
    @Deprecated
    private String buildDeviceName(String mac, String ip, String vendorName) {
        // Use last 4 hex chars of MAC for a short unique suffix
        String suffix = mac.replace(":", "");
        suffix = suffix.substring(Math.max(0, suffix.length() - 4));
        // Use vendor short label if available
        if (vendorName != null && !"Unknown".equals(vendorName)) {
            String shortLabel = macVendorService.shortLabel(vendorName);
            return shortLabel + "-" + suffix;
        }
        return "Device-" + suffix;
    }

    // ==========================================================================
    //  Inner Types
    // ==========================================================================

    /** Immutable snapshot of one ARP table row (IP + normalised MAC) */
    private record ArpEntry(String ip, String mac) {}

    /**
     * Scan result returned by POST /api/devices/scan and stored for
     * GET /api/devices/scan/status.
     *
     * @param totalFound    total live devices found in this scan
     * @param newDevices    devices seen for the first time this scan
     * @param updatedDevices devices already known but with updated IP/status
     * @param removedStale  auto-discovered devices deleted as stale
     * @param scannedAt     timestamp of scan completion
     * @param devices       the actual Device objects discovered
     * @param networkPrefix current network prefix (e.g., "192.168.1")
     * @param networkChanged true if network prefix changed during this scan
     */
    public record ScanResult(
            int totalFound,
            int newDevices,
            int updatedDevices,
            int removedStale,
            LocalDateTime scannedAt,
            List<Device> devices,
            String networkPrefix,
            boolean networkChanged
    ) {
        /** Overload for backward compatibility */
        public ScanResult(int total, int news, int updated, int removed, LocalDateTime scanned, List<Device> devs) {
            this(total, news, updated, removed, scanned, devs, null, false);
        }
    }
}
