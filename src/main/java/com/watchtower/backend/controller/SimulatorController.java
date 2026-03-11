package com.watchtower.backend.controller;

import com.watchtower.backend.service.SimulationControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/simulator")
@Profile("simulator")
@RequiredArgsConstructor
public class SimulatorController {

    private final SimulationControlService controlService;

    @PostMapping("/toggle")
    public ResponseEntity<Map<String, String>> toggle() {
        String status = controlService.toggle();
        return ResponseEntity.ok(Map.of("status", status));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> status() {
        return ResponseEntity.ok(Map.of("status", controlService.getStatus()));
    }
}