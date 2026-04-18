package com.watchtower.backend.observer;

import com.watchtower.backend.entity.Alert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AJT — Observer Pattern: Subject / Publisher.
 * DiagnosisEngine injects this and calls publish(alert) when a rule fires.
 * All registered AlertListeners are notified in order.
 *
 * Spring collects all @Component AlertListeners automatically via constructor injection.
 */
@Slf4j
@Component
public class AlertPublisher {

    private final List<AlertListener> listeners;

    // Spring injects all beans implementing AlertListener
    public AlertPublisher(List<AlertListener> listeners) {
        this.listeners = new ArrayList<>(listeners);
        log.info("AlertPublisher: {} listener(s) registered: {}",
                listeners.size(),
                listeners.stream()
                        .map(l -> l.getClass().getSimpleName())
                        .toList());
    }

    /**
     * Broadcast alert to all registered listeners.
     * Called by DiagnosisEngine when a Strategy rule fires.
     */
    public void publish(Alert alert) {
        log.debug("AlertPublisher: broadcasting {} alert for device {}",
                alert.getSeverity(), alert.getDevice().getDeviceName());

        for (AlertListener listener : listeners) {
            try {
                listener.onAlert(alert);
            } catch (Exception e) {
                log.error("AlertPublisher: listener {} failed — {}",
                        listener.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}
