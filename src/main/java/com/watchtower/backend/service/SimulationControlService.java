package com.watchtower.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class SimulationControlService {

    // AtomicBoolean is thread-safe — SimulatorService runs in a background thread
    // and reads this flag, while HTTP request thread writes it via toggle()
    private final AtomicBoolean running = new AtomicBoolean(true);

    public boolean isRunning() {
        return running.get();
    }

    // Called by POST /api/simulator/toggle
    public String toggle() {
        boolean newState = !running.get();
        running.set(newState);
        String status = newState ? "STARTED" : "PAUSED";
        log.info("Simulator toggled — now: {}", status);
        return status;
    }

    public String getStatus() {
        return running.get() ? "RUNNING" : "PAUSED";
    }
}
