package com.watchtower.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.DeviceGeolocation;
import com.watchtower.backend.repository.DeviceGeolocationRepository;
import com.watchtower.backend.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoLocationService {

    private static final String IP_API_FIELDS = "status,message,countryCode,country,regionName,city,lat,lon,isp,timezone,query";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().build();

    private final DeviceRepository deviceRepository;
    private final DeviceGeolocationRepository deviceGeolocationRepository;
    private final ObjectMapper objectMapper;

    public void locateAndSaveAsync(Device device, boolean forceRefresh) {
        if (device == null || device.getId() == null || device.getIpAddress() == null) return;
        CompletableFuture.runAsync(() -> {
            try {
                locateAndSave(device.getId(), forceRefresh);
            } catch (Exception e) {
                log.debug("GeoLocation: async lookup failed for device {}: {}", device.getId(), e.getMessage());
            }
        });
    }

    @Transactional
    public Optional<DeviceGeolocation> locateAndSave(Long deviceId, boolean forceRefresh) {
        Optional<Device> deviceOpt = deviceRepository.findById(deviceId);
        if (deviceOpt.isEmpty()) return Optional.empty();

        Device device = deviceOpt.get();
        String ip = device.getIpAddress();
        if (ip == null || ip.isBlank()) return Optional.empty();

        Optional<DeviceGeolocation> existingOpt = deviceGeolocationRepository.findByDeviceId(deviceId);
        if (!forceRefresh && existingOpt.isPresent()) {
            DeviceGeolocation existing = existingOpt.get();
            boolean sameIp = ip.equals(existing.getIpAddress());
            boolean recentlyUpdated = existing.getLastUpdated() != null
                    && existing.getLastUpdated().isAfter(LocalDateTime.now().minusHours(8));
            if (sameIp && recentlyUpdated) {
                return existingOpt;
            }
        }

        boolean privateIp = isPrivateIp(ip);
        JsonNode payload = fetchGeoPayload(ip, privateIp);
        if (payload == null) return existingOpt;

        DeviceGeolocation geo = existingOpt.orElseGet(DeviceGeolocation::new);
        geo.setDevice(device);
        geo.setIpAddress(ip);
        geo.setIsPrivate(privateIp);
        geo.setSource("IP_API");
        geo.setCountryCode(text(payload, "countryCode"));
        geo.setCountryName(text(payload, "country"));
        geo.setRegionName(text(payload, "regionName"));
        geo.setCityName(text(payload, "city"));
        geo.setIspName(text(payload, "isp"));
        geo.setTimezone(text(payload, "timezone"));
        geo.setLatitude(number(payload, "lat"));
        geo.setLongitude(number(payload, "lon"));
        geo.setLastUpdated(LocalDateTime.now());

        return Optional.of(deviceGeolocationRepository.save(geo));
    }

    private JsonNode fetchGeoPayload(String ip, boolean privateIp) {
        try {
            String encodedIp = URLEncoder.encode(ip, StandardCharsets.UTF_8);
            String target = privateIp
                    ? "http://ip-api.com/json/?fields=" + IP_API_FIELDS
                    : "http://ip-api.com/json/" + encodedIp + "?fields=" + IP_API_FIELDS;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(target))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.debug("GeoLocation: ip-api returned status {} for ip {}", response.statusCode(), ip);
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!"success".equalsIgnoreCase(text(root, "status"))) {
                log.debug("GeoLocation: ip-api lookup failed for ip {}: {}", ip, text(root, "message"));
                return null;
            }
            return root;
        } catch (Exception e) {
            log.debug("GeoLocation: lookup exception for ip {}: {}", ip, e.getMessage());
            return null;
        }
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) return null;
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static Double number(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull() || !value.isNumber()) return null;
        return value.asDouble();
    }

    private static boolean isPrivateIp(String ip) {
        return ip.startsWith("10.")
                || ip.startsWith("192.168.")
                || ip.startsWith("127.")
                || ip.matches("^172\\.(1[6-9]|2\\d|3[0-1])\\..*");
    }
}
