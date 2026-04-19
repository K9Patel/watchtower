package com.watchtower.backend.service;

import com.watchtower.backend.entity.Device;
import com.watchtower.backend.repository.AlertRepository;
import com.watchtower.backend.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceDetailService {

    private final DeviceRepository deviceRepository;
    private final AlertRepository alertRepository;
    private final AnalysisService analysisService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, String> vendorCache = new ConcurrentHashMap<>();

    public Map<String, Object> getDeviceDetails(Long id, LocalDateTime scopeStart) {
        Device device = deviceRepository.findById(id).orElseThrow();
        Map<String, Object> details = new LinkedHashMap<>();

        details.put("device", device);

        // Uptime (since registered)
        long uptimeHours = ChronoUnit.HOURS.between(device.getRegisteredAt(), LocalDateTime.now());
        details.put("uptime", uptimeHours + " hours");

        // Ping latency
        long latency = -1;
        try {
            InetAddress addr = InetAddress.getByName(device.getIpAddress());
            long start = System.currentTimeMillis();
            if (addr.isReachable(2000)) {
                latency = System.currentTimeMillis() - start;
            }
        } catch (Exception e) {
            log.warn("Could not ping {}", device.getIpAddress());
        }
        details.put("pingLatency", latency);

        // Open Ports (common)
        List<Integer> openPorts = new ArrayList<>();
        int[] commonPorts = {22, 80, 443, 8080};
        for (int port : commonPorts) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(device.getIpAddress(), port), 300);
                openPorts.add(port);
            } catch (Exception ignored) {}
        }
        details.put("openPorts", openPorts);

        // Vendor
        details.put("vendor", getVendorFromMac(device.getMacAddress()));

        // Alerts count
        long alertsCount = alertRepository.findByDeviceAndIsResolvedFalse(device).size();
        details.put("alertsCount", alertsCount);

        // Bandwidth history / share
        Map<String, Double> share = analysisService.getBandwidthSharePerDevice(scopeStart);
        details.put("bandwidthShare", share.getOrDefault(device.getDeviceName(), 0.0));

        return details;
    }

    private String getVendorFromMac(String mac) {
        if (mac == null || mac.length() < 8) return "Unknown Vendor";

        // To comply with dynamic requirement but avoid free-tier strict API rate limiting
        if (vendorCache.containsKey(mac)) {
            return vendorCache.get(mac);
        }

        try {
            // Dynamically query public MAC vendor API
            String url = "https://api.macvendors.com/" + mac;
            String vendor = restTemplate.getForObject(url, String.class);
            if (vendor != null && !vendor.trim().isEmpty()) {
                vendorCache.put(mac, vendor.trim());
                return vendor.trim();
            }
        } catch (RestClientException e) {
            log.warn("Failed to fetch MAC vendor for {} (possibly rate limited): {}", mac, e.getMessage());
        }
        
        return "Generic Network Device";
    }
}
