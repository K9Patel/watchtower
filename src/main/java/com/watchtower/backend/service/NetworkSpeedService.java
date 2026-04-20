package com.watchtower.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects and reports network interface speeds (WiFi, Ethernet).
 * Supports Windows (WMI), Linux (sysfs), and macOS.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkSpeedService {

    private volatile Long cachedInterfaceSpeed = null;
    private volatile Long lastSpeedCheckTime = 0L;
    private static final long CACHE_DURATION_MS = 30000; // 30 second cache (matches frontend polling)
    
    private volatile Long cachedThroughput = null;
    private volatile Long lastThroughputCheckTime = 0L;
    private static final long THROUGHPUT_CACHE_DURATION_MS = 60000; // 1 minute cache for throughput (heavier operation)

    /**
     * Get the speed of the active network interface in Mbps + actual internet throughput.
     * Returns cached values if available.
     */
    public Map<String, Object> getActiveInterfaceSpeed() {
        long now = System.currentTimeMillis();
        
        // Get WiFi link speed (cached for 30s)
        Long linkSpeed = cachedInterfaceSpeed;
        if (linkSpeed == null || (now - lastSpeedCheckTime) >= CACHE_DURATION_MS) {
            linkSpeed = detectInterfaceSpeed();
            if (linkSpeed != null) {
                cachedInterfaceSpeed = linkSpeed;
                lastSpeedCheckTime = now;
            }
        }
        
        // Get internet throughput (cached for 60s - heavier operation)
        Long throughputMbps = cachedThroughput;
        if (throughputMbps == null || (now - lastThroughputCheckTime) >= THROUGHPUT_CACHE_DURATION_MS) {
            throughputMbps = measureInternetThroughput();
            if (throughputMbps != null) {
                cachedThroughput = throughputMbps;
                lastThroughputCheckTime = now;
            }
        }

        return buildSpeedResponse(linkSpeed, throughputMbps);
    }

    /**
     * Detect interface speed via OS-specific methods.
     * Windows: WMI query → Win32_NetworkAdapter.Speed
     * Linux: /sys/class/net/{iface}/speed
     * macOS: ifconfig parsing
     */
    private Long detectInterfaceSpeed() {
        try {
            // Find the active non-loopback interface
            NetworkInterface activeNic = getActiveNetworkInterface();
            if (activeNic == null) {
                log.warn("NetworkSpeed: no active network interface found");
                return null;
            }

            String os = System.getProperty("os.name").toLowerCase();
            long speed = -1;

            if (os.contains("win")) {
                speed = getWindowsInterfaceSpeed(activeNic);
            } else if (os.contains("linux")) {
                speed = getLinuxInterfaceSpeed(activeNic);
            } else if (os.contains("mac")) {
                speed = getMacInterfaceSpeed(activeNic);
            }

            if (speed > 0) {
                log.info("NetworkSpeed: {} interface {} detected at {} Mbps", 
                    os, activeNic.getName(), speed);
                return speed;
            }

        } catch (Exception e) {
            log.warn("NetworkSpeed: failed to detect interface speed", e);
        }

        return null;
    }

    /**
     * Windows: Query WMI for network adapter speed.
     * Speed is in bits/sec, convert to Mbps.
     * Query all active adapters and pick the best (WiFi preferred, then highest speed).
     */
    private long getWindowsInterfaceSpeed(NetworkInterface nic) {
        try {
            // Query all network adapters with non-zero speed
            ProcessBuilder pb = new ProcessBuilder(
                "wmic", "path", "Win32_NetworkAdapter",
                "where", "Speed > 0 and NetConnectionStatus = 2",
                "get", "Name,Speed,Description", "/format:list"
            );
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            
            long bestSpeed = -1;
            String bestName = "";
            boolean foundWiFi = false;
            
            String[] entries = output.split("\n\n");
            for (String entry : entries) {
                String name = "";
                long speed = -1;
                String description = "";
                
                for (String line : entry.split("\n")) {
                    if (line.startsWith("Name=")) {
                        name = line.replace("Name=", "").trim();
                    }
                    if (line.startsWith("Speed=")) {
                        String speedStr = line.replace("Speed=", "").trim();
                        if (!speedStr.isEmpty() && !speedStr.equals("0")) {
                            speed = Long.parseLong(speedStr) / 1_000_000;
                        }
                    }
                    if (line.startsWith("Description=")) {
                        description = line.replace("Description=", "").trim();
                    }
                }
                
                // Check if this is a WiFi adapter
                boolean isWiFi = description.toLowerCase().contains("wifi") || 
                                description.toLowerCase().contains("wireless") ||
                                description.toLowerCase().contains("802.11");
                
                // Prefer WiFi, then highest speed
                if (speed > 0 && !isVirtualAdapterName(name)) {
                    if (!foundWiFi && isWiFi) {
                        // First WiFi adapter found
                        foundWiFi = true;
                        bestSpeed = speed;
                        bestName = name;
                    } else if (foundWiFi && isWiFi) {
                        // Compare with other WiFi adapters
                        if (speed > bestSpeed) {
                            bestSpeed = speed;
                            bestName = name;
                        }
                    } else if (!foundWiFi && speed > bestSpeed) {
                        // Non-WiFi but highest so far
                        bestSpeed = speed;
                        bestName = name;
                    }
                }
                
                log.debug("NetworkSpeed: WMI adapter {} - {} Mbps ({})", name, speed, description);
            }
            
            if (bestSpeed > 0) {
                log.info("NetworkSpeed: Selected adapter {} at {} Mbps (WiFi: {})", 
                    bestName, bestSpeed, foundWiFi);
                return bestSpeed;
            }
        } catch (Exception e) {
            log.debug("NetworkSpeed: WMI query failed", e);
        }
        return -1;
    }

    private boolean isVirtualAdapterName(String name) {
        String lower = name.toLowerCase();
        return lower.contains("virtual") || lower.contains("vmware") || 
               lower.contains("vbox") || lower.contains("docker") ||
               lower.contains("npcap") || lower.contains("debug");
    }

    /**
     * Linux: Read from /sys/class/net/{interface}/speed
     * Returns speed in Mbps directly.
     */
    private long getLinuxInterfaceSpeed(NetworkInterface nic) {
        try {
            String speedFile = "/sys/class/net/" + nic.getName() + "/speed";
            String speedStr = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(speedFile))).trim();
            
            long speed = Long.parseLong(speedStr);
            if (speed > 0) {
                return speed;
            }
        } catch (Exception e) {
            log.debug("NetworkSpeed: Linux sysfs read failed", e);
        }
        return -1;
    }

    /**
     * macOS: Parse 'ifconfig' output for inet6 nd6 flags.
     * Fallback: check for common WiFi adapter speeds via system_profiler.
     */
    private long getMacInterfaceSpeed(NetworkInterface nic) {
        try {
            // Try system_profiler for Wi-Fi info
            ProcessBuilder pb = new ProcessBuilder("system_profiler", "SPAirPortDataType");
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            
            // Look for "Connection Speed" in output
            for (String line : output.split("\n")) {
                if (line.contains("Connection Speed") || line.contains("PHY Mode")) {
                    log.debug("NetworkSpeed: macOS WiFi info: {}", line);
                    // Extract numbers from pattern like "867 Mbps" or "1300 Mbps"
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d+)\\s*Mbps");
                    java.util.regex.Matcher m = p.matcher(line);
                    if (m.find()) {
                        return Long.parseLong(m.group(1));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("NetworkSpeed: macOS query failed", e);
        }
        return -1;
    }

    /**
     * Find the active non-loopback, non-virtual network interface.
     */
    private NetworkInterface getActiveNetworkInterface() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        
        List<NetworkInterface> candidates = new ArrayList<>();
        
        for (NetworkInterface nic : Collections.list(interfaces)) {
            // Skip loopback and virtual interfaces
            if (nic.isLoopback() || !nic.isUp() || isVirtualInterface(nic)) {
                continue;
            }
            
            // Prefer WiFi (wlan, en, etc) over other interfaces
            candidates.add(nic);
        }
        
        // Sort: WiFi first, then Ethernet, by display name
        candidates.sort((a, b) -> {
            String aName = a.getDisplayName().toLowerCase();
            String bName = b.getDisplayName().toLowerCase();
            
            // WiFi/wireless first
            boolean aIsWifi = aName.contains("wifi") || aName.contains("wireless") || aName.contains("wlan");
            boolean bIsWifi = bName.contains("wifi") || bName.contains("wireless") || bName.contains("wlan");
            
            if (aIsWifi != bIsWifi) {
                return aIsWifi ? -1 : 1;
            }
            
            return aName.compareTo(bName);
        });
        
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private boolean isVirtualInterface(NetworkInterface nic) {
        String name = nic.getName().toLowerCase();
        String display = nic.getDisplayName().toLowerCase();
        String combined = name + " " + display;
        
        return combined.contains("virtual")
            || combined.contains("vmware")
            || combined.contains("vbox")
            || combined.contains("docker")
            || combined.contains("tap")
            || combined.contains("tunnel")
            || combined.contains("npcap");
    }

    /**
     * Measure actual internet throughput by downloading a test file.
     * Uses Cloudflare CDN for reliable, fast downloads from anywhere.
     * Returns throughput in Mbps, or null if measurement fails.
     */
    private Long measureInternetThroughput() {
        try {
            // Use a small file from Cloudflare CDN (1MB test file)
            String testUrl = "https://speed.cloudflare.com/__down?bytes=1000000";
            
            java.net.URL url = new java.net.URL(testUrl);
            java.net.URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "Watchtower-NetworkSpeed/1.0");
            
            long startTime = System.nanoTime();
            long bytesDownloaded = 0;
            
            try (java.io.InputStream is = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    bytesDownloaded += bytesRead;
                }
            }
            
            long endTime = System.nanoTime();
            long durationMs = (endTime - startTime) / 1_000_000;
            
            // Avoid division by zero
            if (durationMs < 100) {
                durationMs = 100;
            }
            
            // Calculate Mbps: (bytes * 8 bits/byte) / (time in seconds) / 1,000,000
            double throughputMbps = (bytesDownloaded * 8.0) / (durationMs / 1000.0) / 1_000_000;
            
            log.info("NetworkSpeed: Internet throughput measured: {} Mbps ({} bytes in {} ms)",
                    String.format("%.2f", throughputMbps), bytesDownloaded, durationMs);
            
            return Math.round(throughputMbps);
            
        } catch (Exception e) {
            log.debug("NetworkSpeed: Internet throughput measurement failed (may indicate offline or firewall block)", e);
            return null;
        }
    }

    private Map<String, Object> buildSpeedResponse(Long linkSpeedMbps, Long throughputMbps) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", new Date().toString());
        
        // WiFi Link Speed (hardware capability)
        if (linkSpeedMbps != null && linkSpeedMbps > 0) {
            response.put("wifiLinkSpeed", linkSpeedMbps);
            response.put("interfaceType", classifySpeed(linkSpeedMbps));
        } else {
            response.put("wifiLinkSpeed", 0);
            response.put("interfaceType", "unknown");
        }
        
        // Internet Throughput (actual usable speed)
        if (throughputMbps != null && throughputMbps > 0) {
            response.put("internetThroughput", throughputMbps);
            response.put("throughputStatus", "measured");
        } else {
            response.put("internetThroughput", 0);
            response.put("throughputStatus", throughputMbps == null ? "measuring" : "unavailable");
        }
        
        // Overall status
        response.put("status", linkSpeedMbps != null && linkSpeedMbps > 0 ? "active" : "unknown");
        
        return response;
    }

    /**
     * Classify network interface type based on speed.
     */
    private String classifySpeed(long speedMbps) {
        if (speedMbps >= 10000) {
            return "10G Ethernet";
        } else if (speedMbps >= 1000) {
            return "Gigabit Ethernet";
        } else if (speedMbps >= 433) {
            return "5GHz WiFi (AC/AX)";
        } else if (speedMbps >= 150) {
            return "WiFi (N/AC)";
        } else if (speedMbps >= 54) {
            return "WiFi (G)";
        } else if (speedMbps >= 11) {
            return "WiFi (B)";
        } else if (speedMbps >= 100) {
            return "Fast Ethernet";
        } else {
            return "Ethernet";
        }
    }

    /**
     * Get all available network interfaces with their speeds.
     */
    public List<Map<String, Object>> getAllInterfacesSpeeds() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            List<Map<String, Object>> result = new ArrayList<>();
            
            for (NetworkInterface nic : Collections.list(interfaces)) {
                if (nic.isLoopback() || !nic.isUp() || isVirtualInterface(nic)) {
                    continue;
                }
                
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("name", nic.getName());
                info.put("displayName", nic.getDisplayName());
                info.put("hardwareAddress", formatHardwareAddress(nic.getHardwareAddress()));
                info.put("mtu", nic.getMTU());
                
                // Try to get speed (OS-specific, may not be available)
                long speed = queryInterfaceSpeed(nic);
                if (speed > 0) {
                    info.put("speedMbps", speed);
                    info.put("interfaceType", classifySpeed(speed));
                } else {
                    info.put("speedMbps", null);
                    info.put("interfaceType", "unknown");
                }
                
                result.add(info);
            }
            
            return result;
        } catch (SocketException e) {
            log.warn("NetworkSpeed: failed to enumerate interfaces", e);
            return List.of();
        }
    }

    private long queryInterfaceSpeed(NetworkInterface nic) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                return getWindowsInterfaceSpeed(nic);
            } else if (os.contains("linux")) {
                return getLinuxInterfaceSpeed(nic);
            } else if (os.contains("mac")) {
                return getMacInterfaceSpeed(nic);
            }
        } catch (Exception e) {
            log.debug("NetworkSpeed: failed to query {} speed", nic.getName(), e);
        }
        return -1;
    }

    private String formatHardwareAddress(byte[] hw) {
        if (hw == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hw.length; i++) {
            sb.append(String.format("%02X%s", hw[i], (i < hw.length - 1) ? ":" : ""));
        }
        return sb.toString();
    }
}
