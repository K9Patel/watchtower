package com.watchtower.backend.service;

import com.watchtower.backend.dto.DpiDeviceDto;
import com.watchtower.backend.dto.DpiTrafficEntryDto;
import com.watchtower.backend.dto.ServiceBreakdownDto;
import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.DpiTraffic;
import com.watchtower.backend.repository.DeviceRepository;
import com.watchtower.backend.repository.DpiTrafficRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DpiQueryService {

    private final DeviceRepository deviceRepository;
    private final DpiTrafficRepository dpiTrafficRepository;

    @Transactional(readOnly = true)
    public DpiDeviceDto getDeviceSnapshot(Long deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));

        DpiTraffic latest = dpiTrafficRepository.findTopByDeviceOrderByClassifiedAtDesc(device).orElse(null);

        return DpiDeviceDto.builder()
                .deviceId(device.getId())
                .deviceName(device.getDeviceName())
                .ipAddress(device.getIpAddress())
                .currentService(latest != null ? latest.getServiceName() : nullSafe(device.getCurrentService(), "UNKNOWN"))
                .currentCategory(latest != null ? latest.getTrafficCategory() : nullSafe(device.getCurrentCategory(), "UNKNOWN"))
                .sniHostname(latest != null ? latest.getSniHostname() : device.getCurrentSniHostname())
                .destinationIp(latest != null ? latest.getDestinationIp() : device.getCurrentDestinationIp())
                .destinationPort(latest != null ? latest.getPort() : device.getCurrentDestinationPort())
                .confidence(latest != null ? latest.getConfidence() : (short) 0)
                .lastUpdated(latest != null ? latest.getClassifiedAt() : device.getDpiLastUpdated())
                .build();
    }

    @Transactional(readOnly = true)
    public List<DpiTrafficEntryDto> getDeviceHistory(Long deviceId, String range) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));

        LocalDateTime since = resolveSince(range);

        return dpiTrafficRepository.findByDeviceAndClassifiedAtAfterOrderByClassifiedAtDesc(device, since)
                .stream()
                .map(this::toEntryDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceBreakdownDto> getBreakdown(String range) {
        LocalDateTime since = resolveSince(range);
        List<Object[]> rows = dpiTrafficRepository.summarizeByServiceSince(since);
        long totalEvents = rows.stream().mapToLong(row -> asLong(row[1])).sum();

        return rows.stream().map(row -> {
            String service = String.valueOf(row[0]);
            long events = asLong(row[1]);
            long bytes = asLong(row[2]);
            double avgConfidence = asDouble(row[3]);
            double share = totalEvents > 0 ? (events * 100.0) / totalEvents : 0.0;

            return ServiceBreakdownDto.builder()
                    .serviceName(service)
                    .trafficCategory(DpiClassifierService.categoryForService(service))
                    .events(events)
                    .bytesCaptured(bytes)
                    .sharePercent(round2(share))
                    .averageConfidence(round2(avgConfidence))
                    .build();
        }).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getWeeklySummary() {
        List<ServiceBreakdownDto> breakdown = getBreakdown("7d");
        long totalEvents = breakdown.stream().mapToLong(item -> item.getEvents() == null ? 0L : item.getEvents()).sum();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("range", "7d");
        payload.put("generatedAt", LocalDateTime.now().toString());
        payload.put("totalEvents", totalEvents);
        payload.put("breakdown", breakdown);
        return payload;
    }

    private DpiTrafficEntryDto toEntryDto(DpiTraffic item) {
        return DpiTrafficEntryDto.builder()
                .id(item.getId())
                .serviceName(item.getServiceName())
                .trafficCategory(item.getTrafficCategory())
                .sniHostname(item.getSniHostname())
                .destinationIp(item.getDestinationIp())
                .port(item.getPort())
                .packetsCount(item.getPacketsCount())
                .bytesCaptured(item.getBytesCaptured())
                .confidence(item.getConfidence())
                .classifiedAt(item.getClassifiedAt())
                .build();
    }

    private LocalDateTime resolveSince(String range) {
        String normalized = (range == null ? "1h" : range.trim().toLowerCase(Locale.ROOT));
        LocalDateTime now = LocalDateTime.now();

        return switch (normalized) {
            case "1h" -> now.minusHours(1);
            case "6h" -> now.minusHours(6);
            case "24h" -> now.minusHours(24);
            case "7d" -> now.minusDays(7);
            default -> now.minusHours(1);
        };
    }

    private String nullSafe(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
