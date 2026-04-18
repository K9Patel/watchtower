package com.watchtower.backend.controller;

import com.watchtower.backend.service.AnalysisService;
import com.watchtower.backend.service.SimulationControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class StatsController {

    private final AnalysisService analysisService;
    private final SimulationControlService controlService;
    private final Environment environment;

    // GET /api/stats/summary — main dashboard card data
    // Returns: totalLoadPercent, topConsumer, onlineDevices, totalDevices,
    //          trafficBreakdown, bandwidthShare, activeProfile, timestamp
    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = analysisService.getFullSummary();
        // Enrich with live simulator/real mode badge
        String activeProfile = Arrays.stream(environment.getActiveProfiles())
                .findFirst().orElse("default");
        summary.put("activeProfile", activeProfile);
        // In real mode, show LIVE_NETWORK; in simulator mode, show RUNNING/PAUSED
        summary.put("simulatorStatus",
                activeProfile.equalsIgnoreCase("real") ? "LIVE_NETWORK" : controlService.getStatus());
        return summary;
    }

    // GET /api/stats/traffic — traffic type breakdown (donut chart)
    @GetMapping("/traffic")
    public Map<String, Long> getTrafficBreakdown() {
        return analysisService.getTrafficBreakdown();
    }

    // GET /api/stats/bandwidth-share — % bandwidth per device (bar chart)
    @GetMapping("/bandwidth-share")
    public Map<String, Double> getBandwidthShare() {
        return analysisService.getBandwidthSharePerDevice();
    }

    // GET /api/stats/load — single total load % (live gauge update)
    @GetMapping("/load")
    public Map<String, Double> getTotalLoad() {
        return Map.of("totalLoadPercent", analysisService.getTotalLoad());
    }

    // GET /api/stats/mode — active profile badge: SIMULATOR or REAL
    @GetMapping("/mode")
    public Map<String, String> getMode() {
        String profile = Arrays.stream(environment.getActiveProfiles())
                .findFirst().orElse("simulator");
        return Map.of(
                "profile", profile,
                "label",   profile.equalsIgnoreCase("real") ? "🟢 LIVE" : "🔵 SIMULATOR",
                "simulatorStatus", controlService.getStatus()
        );
    }
}
