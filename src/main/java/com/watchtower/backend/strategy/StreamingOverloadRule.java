package com.watchtower.backend.strategy;

import com.watchtower.backend.entity.Alert;
import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.DpiTraffic;
import com.watchtower.backend.entity.UsageLog;
import com.watchtower.backend.repository.DpiTrafficRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Strategy rule: streaming overload based on recent DPI classifications.
 * Fires when multiple unique devices are currently classified as STREAMING.
 * Severity: HIGH
 */
@Component
@RequiredArgsConstructor
public class StreamingOverloadRule implements DiagnosisRule {

        private static final int STREAMING_DEVICE_THRESHOLD = 3;
        private static final int LOOKBACK_SECONDS = 90;

        private final DpiTrafficRepository dpiTrafficRepository;

    @Override
    public Optional<Alert> evaluate(Device device, List<UsageLog> recentLogs) {
                LocalDateTime since = LocalDateTime.now().minusSeconds(LOOKBACK_SECONDS);
                List<DpiTraffic> streaming = dpiTrafficRepository.findRecentStreamingSince(since);
                if (streaming.isEmpty()) {
                        return Optional.empty();
                }

                // Keep only latest streaming classification per device.
                Map<Long, DpiTraffic> latestByDevice = new LinkedHashMap<>();
                for (DpiTraffic entry : streaming) {
                        if (entry.getDevice() == null || entry.getDevice().getId() == null) {
                                continue;
                        }
                        latestByDevice.putIfAbsent(entry.getDevice().getId(), entry);
                }

                if (latestByDevice.size() < STREAMING_DEVICE_THRESHOLD) {
                        return Optional.empty();
                }

                // Avoid duplicate alerts: only the smallest device ID emits the network-level alert.
                Long ownerId = latestByDevice.keySet().stream().min(Long::compareTo).orElse(null);
                if (ownerId == null || !ownerId.equals(device.getId())) {
                        return Optional.empty();
                }

                Map<String, Long> serviceCounts = latestByDevice.values().stream()
                                .collect(Collectors.groupingBy(
                                                entry -> displayService(entry.getServiceName()),
                                                Collectors.counting()
                                ));

                String breakdown = serviceCounts.entrySet().stream()
                                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                                .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                                .collect(Collectors.joining(", "));

                String message = latestByDevice.size() + " devices streaming - " + breakdown;

                return Optional.of(Alert.builder()
                                .device(device)
                                .alertType("STREAMING_OVERLOAD")
                                .severity(Alert.Severity.HIGH)
                                .message(message)
                                .build());
        }

        private String displayService(String rawService) {
                if (rawService == null || rawService.isBlank()) {
                        return "Unknown";
                }

                String normalized = rawService.trim().toUpperCase(Locale.ROOT);
                return switch (normalized) {
                        case "YOUTUBE" -> "YouTube";
                        case "NETFLIX" -> "Netflix";
                        case "WHATSAPP" -> "WhatsApp";
                        case "BGMI" -> "BGMI";
                        case "ZOOM" -> "Zoom";
                        default -> rawService.replace('_', ' ');
                };
    }
}
