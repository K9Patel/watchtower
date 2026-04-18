package com.watchtower.backend.service;

import com.watchtower.backend.entity.Alert;
import com.watchtower.backend.entity.Device;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Service responsible for pushing real-time updates over WebSocket to the React frontend.
 * Uses SimpMessagingTemplate provided by spring-boot-starter-websocket.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Pushes a dashboard summary update.
     * Called by RealDataService after saving a batch of UsageLogs.
     */
    public void pushDashboardUpdate(Map<String, Object> summaryData) {
        log.debug("WebSocket: Pushing dashboard update to /topic/dashboard");
        messagingTemplate.convertAndSend("/topic/dashboard", summaryData);
    }

    /**
     * Pushes a new alert.
     * Called when DiagnosisEngine fires a rule.
     */
    public void pushAlert(Alert alert) {
        log.debug("WebSocket: Pushing alert {} to /topic/alerts", alert.getAlertType());
        messagingTemplate.convertAndSend("/topic/alerts", alert);
    }

    /**
     * Pushes a device status update.
     * Called when a device comes online, goes offline, or is newly discovered.
     */
    public void pushDeviceUpdate(Device device) {
        log.debug("WebSocket: Pushing device update {} to /topic/devices", device.getDeviceName());
        messagingTemplate.convertAndSend("/topic/devices", device);
    }

    /**
     * Sends an arbitrary message to a topic.
     * Used for network change events and other custom messages.
     */
    public void pushMessage(String topic, Object message) {
        log.debug("WebSocket: Pushing message to {}", topic);
        messagingTemplate.convertAndSend(topic, message);
    }
}
