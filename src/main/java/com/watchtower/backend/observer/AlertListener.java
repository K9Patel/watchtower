package com.watchtower.backend.observer;

import com.watchtower.backend.entity.Alert;

/**
 * AJT — Observer Pattern: Observer interface.
 * Every listener that wants to react to a new Alert must implement this.
 * DiagnosisEngine calls AlertPublisher.publish() which notifies all registered listeners.
 */
public interface AlertListener {
    void onAlert(Alert alert);
}
