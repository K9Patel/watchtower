package com.watchtower.backend.observer;

import com.watchtower.backend.entity.Alert;
import com.watchtower.backend.service.DashboardWebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AJT Unit 3 — WebSocket Integration:
 * Observer component that listens for new alerts from DiagnosisEngine
 * and pushes them over WebSocket to the React frontend instantly.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAlertListener implements AlertListener {

    private final DashboardWebSocketService webSocketService;

    @Override
    public void onAlert(Alert alert) {
        log.debug("WebSocketAlertListener: pushing alert {} to frontend", alert.getAlertType());
        webSocketService.pushAlert(alert);
    }
}
