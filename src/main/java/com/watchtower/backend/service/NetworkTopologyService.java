package com.watchtower.backend.service;

import com.watchtower.backend.entity.Alert;
import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.DeviceBaseline;
import com.watchtower.backend.entity.DeviceGeolocation;
import com.watchtower.backend.repository.AlertRepository;
import com.watchtower.backend.repository.DeviceBaselineRepository;
import com.watchtower.backend.repository.DeviceGeolocationRepository;
import com.watchtower.backend.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkTopologyService {

    private static final long CACHE_TTL_MS = 15_000;
    private static final Pattern IPV4_PATTERN = Pattern.compile("(\\d{1,3}(?:\\.\\d{1,3}){3})");

    private final DeviceRepository deviceRepository;
    private final AlertRepository alertRepository;
    private final DeviceBaselineRepository baselineRepository;
    private final DeviceGeolocationRepository geolocationRepository;
    private final NetworkMonitorService networkMonitorService;

    private volatile Map<String, Object> cachedTopology;
    private volatile long cachedAt;

    public Map<String, Object> getTopology() {
        long now = System.currentTimeMillis();
        if (cachedTopology != null && (now - cachedAt) < CACHE_TTL_MS) {
            return cachedTopology;
        }

        Map<String, Object> topology = buildTopology();
        cachedTopology = topology;
        cachedAt = now;
        return topology;
    }

    private Map<String, Object> buildTopology() {
        LocalDateTime generatedAt = LocalDateTime.now();
        List<Device> devices = deviceRepository.findByIsActiveTrue();
        String gatewayIp = detectGatewayIp(devices);
        String prefix = networkPrefix(gatewayIp);

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        Optional<Device> gatewayDevice = devices.stream()
                .filter(d -> gatewayIp != null && gatewayIp.equals(d.getIpAddress()))
                .findFirst();

        Double gatewayLat = null;
        Double gatewayLng = null;
        String gatewayLocation = null;
        String gatewayLocationSource = null;
        if (gatewayDevice.isPresent()) {
            Optional<DeviceGeolocation> geo = geolocationRepository.findByDeviceId(gatewayDevice.get().getId());
            if (geo.isPresent()) {
                gatewayLat = geo.get().getLatitude();
                gatewayLng = geo.get().getLongitude();
                gatewayLocation = joinLocation(geo.get().getCityName(), geo.get().getRegionName(), geo.get().getCountryName());
                gatewayLocationSource = geo.get().getSource();
            }
        }

        Map<String, Object> gatewayNode = new LinkedHashMap<>();
        gatewayNode.put("id", "gateway");
        gatewayNode.put("label", "Router / Gateway");
        gatewayNode.put("ip", gatewayIp);
        gatewayNode.put("type", "GATEWAY");
        gatewayNode.put("riskLevel", "SAFE");
        gatewayNode.put("status", "ONLINE");
        gatewayNode.put("currentMbps", 0.0);
        gatewayNode.put("alertCount", 0);
        gatewayNode.put("driftLevel", "NONE");
        nodes.add(gatewayNode);

        int onlineDevices = 0;
        double totalBandwidth = 0.0;

        for (Device device : devices) {
            if (gatewayIp != null && gatewayIp.equals(device.getIpAddress())) {
                continue;
            }

            double currentMbps = Math.max(0.0, networkMonitorService.getCurrentMbps(device));
            totalBandwidth += currentMbps;
            if ("ONLINE".equalsIgnoreCase(device.getStatus())) {
                onlineDevices++;
            }

            List<Alert> unresolved = alertRepository.findByDeviceAndIsResolvedFalse(device);
            int alertCount = unresolved.size();
            Alert.Severity maxSeverity = unresolved.stream()
                    .map(Alert::getSeverity)
                    .max(Comparator.comparingInt(Enum::ordinal))
                    .orElse(null);

            String driftLevel = computeDriftLevel(device, currentMbps);
            String riskLevel = deriveRiskLevel(device, maxSeverity, driftLevel);

            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", device.getId());
            node.put("label", device.getDeviceName() != null ? device.getDeviceName() : device.getIpAddress());
            node.put("ip", device.getIpAddress());
            node.put("mac", device.getMacAddress());
            node.put("vendor", device.getVendorName());
            node.put("osType", device.getOsType());
            node.put("status", device.getStatus());
            node.put("currentMbps", Math.round(currentMbps * 100.0) / 100.0);
            node.put("alertCount", alertCount);
            node.put("severity", maxSeverity != null ? maxSeverity.name() : null);
            node.put("driftLevel", driftLevel);
            node.put("riskLevel", riskLevel);
            node.put("type", "DEVICE");
            nodes.add(node);

            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("source", "gateway");
            edge.put("target", device.getId());
            edge.put("bandwidth", Math.round(currentMbps * 100.0) / 100.0);
            edge.put("status", device.getStatus());
            edges.add(edge);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", generatedAt.toString());
        result.put("gatewayIp", gatewayIp);
        result.put("networkPrefix", prefix);
        result.put("gatewayLat", gatewayLat);
        result.put("gatewayLng", gatewayLng);
        result.put("gatewayLocation", gatewayLocation);
        result.put("gatewayLocationSource", gatewayLocationSource);
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("totalDevices", Math.max(0, nodes.size() - 1));
        result.put("onlineDevices", onlineDevices);
        result.put("totalBandwidthMbps", Math.round(totalBandwidth * 100.0) / 100.0);

        return result;
    }

    @Transactional
    public Map<String, Object> saveGatewayManualLocation(Double latitude, Double longitude, String addressLabel) {
        if (latitude == null || longitude == null || !Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            throw new IllegalArgumentException("Latitude and longitude are required.");
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Latitude/longitude values are out of range.");
        }

        List<Device> devices = deviceRepository.findByIsActiveTrue();
        String gatewayIp = detectGatewayIp(devices);

        Optional<Device> gatewayDevice = devices.stream()
                .filter(d -> gatewayIp != null && gatewayIp.equals(d.getIpAddress()))
                .findFirst();

        if (gatewayDevice.isEmpty()) {
            throw new IllegalStateException("Gateway device not found in active devices. Run network scan and try again.");
        }

        Device device = gatewayDevice.get();
        DeviceGeolocation geo = geolocationRepository.findByDeviceId(device.getId())
                .orElseGet(DeviceGeolocation::new);

        String cleanedAddressLabel = addressLabel == null ? "" : addressLabel.trim();
        String locationLabel = cleanedAddressLabel.isBlank() ? "Manual router location" : cleanedAddressLabel;

        geo.setDevice(device);
        geo.setIpAddress(gatewayIp != null ? gatewayIp : device.getIpAddress());
        geo.setLatitude(latitude);
        geo.setLongitude(longitude);
        geo.setCityName(locationLabel);
        geo.setRegionName(null);
        geo.setCountryName(null);
        geo.setCountryCode(null);
        geo.setIspName(null);
        geo.setTimezone(null);
        geo.setIsPrivate(true);
        geo.setSource("MANUAL");
        geo.setLastUpdated(LocalDateTime.now());

        geolocationRepository.save(geo);

        cachedTopology = null;
        cachedAt = 0;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("updated", true);
        payload.put("gatewayIp", gatewayIp);
        payload.put("gatewayLat", latitude);
        payload.put("gatewayLng", longitude);
        payload.put("gatewayLocation", locationLabel);
        payload.put("gatewayLocationSource", "MANUAL");
        payload.put("message", "Gateway location updated to manual coordinates.");
        return payload;
    }

    private String deriveRiskLevel(Device device, Alert.Severity maxSeverity, String driftLevel) {
        if (!"ONLINE".equalsIgnoreCase(device.getStatus())) {
            return "SAFE";
        }
        if (maxSeverity == Alert.Severity.CRITICAL) return "CRITICAL";
        if (maxSeverity == Alert.Severity.HIGH) return "HIGH_RISK";
        if (maxSeverity == Alert.Severity.MEDIUM) return "SUSPICIOUS";
        if ("HIGH".equals(driftLevel)) return "HIGH_RISK";
        if ("MEDIUM".equals(driftLevel)) return "SUSPICIOUS";
        return "SAFE";
    }

    private String computeDriftLevel(Device device, double currentMbps) {
        if (!Boolean.TRUE.equals(device.getBaselineReady())) {
            return "NONE";
        }

        short hour = (short) LocalDateTime.now().getHour();
        Optional<DeviceBaseline> baseline = baselineRepository.findByDeviceIdAndHourOfDay(device.getId(), hour);
        if (baseline.isEmpty() || baseline.get().getStddevBandwidth() <= 0) {
            return "NONE";
        }

        double diff = Math.abs(currentMbps - baseline.get().getAvgBandwidth());
        double z = diff / baseline.get().getStddevBandwidth();
        if (z >= 3) return "HIGH";
        if (z >= 2) return "MEDIUM";
        if (z >= 1) return "LOW";
        return "NONE";
    }

    private String detectGatewayIp(List<Device> devices) {
        String fromIpConfig = detectFromIpConfig();
        if (fromIpConfig != null) return fromIpConfig;

        String fromRoute = detectFromIpRoute();
        if (fromRoute != null) return fromRoute;

        Optional<String> dbFallback = devices.stream()
                .map(Device::getIpAddress)
                .filter(ip -> ip != null && ip.endsWith(".1"))
                .findFirst();
        return dbFallback.orElse("192.168.1.1");
    }

    private String detectFromIpConfig() {
        try {
            Process process = Runtime.getRuntime().exec("ipconfig");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                boolean waitingNextLine = false;
                while ((line = reader.readLine()) != null) {
                    String lower = line.toLowerCase();
                    if (lower.contains("default gateway")) {
                        Matcher matcher = IPV4_PATTERN.matcher(line);
                        if (matcher.find()) {
                            return matcher.group(1);
                        }
                        waitingNextLine = true;
                        continue;
                    }
                    if (waitingNextLine) {
                        Matcher matcher = IPV4_PATTERN.matcher(line);
                        if (matcher.find()) {
                            return matcher.group(1);
                        }
                        if (!line.trim().isEmpty()) {
                            waitingNextLine = false;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Topology: ipconfig gateway detection failed: {}", e.getMessage());
        }
        return null;
    }

    private String detectFromIpRoute() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", "ip route | grep default"});
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line != null) {
                    Matcher matcher = Pattern.compile("default\\s+via\\s+(\\d{1,3}(?:\\.\\d{1,3}){3})").matcher(line);
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Topology: ip route gateway detection failed: {}", e.getMessage());
        }
        return null;
    }

    private String networkPrefix(String ip) {
        if (ip == null) return "unknown";
        String[] parts = ip.split("\\.");
        if (parts.length >= 3) {
            return parts[0] + "." + parts[1] + "." + parts[2] + ".0/24";
        }
        return ip;
    }

    private String joinLocation(String city, String region, String country) {
        List<String> values = new ArrayList<>();
        if (city != null && !city.isBlank()) values.add(city);
        if (region != null && !region.isBlank()) values.add(region);
        if (country != null && !country.isBlank()) values.add(country);
        return values.isEmpty() ? null : String.join(", ", values);
    }
}
