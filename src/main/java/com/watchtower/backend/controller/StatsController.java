package com.watchtower.backend.controller;

import com.watchtower.backend.service.AnalysisService;
import com.watchtower.backend.service.IncidentTimelineService;
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
    private final IncidentTimelineService incidentTimelineService;
    private final Environment environment;

    // GET /api/stats/summary — main dashboard card data
    // Returns: totalLoadPercent, topConsumer, onlineDevices, totalDevices,
    //          trafficBreakdown, bandwidthShare, activeProfile, timestamp
    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = analysisService.getFullSummary();
        String activeProfile = Arrays.stream(environment.getActiveProfiles())
                .findFirst().orElse("real");
        summary.put("activeProfile", activeProfile);
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

    // GET /api/stats/mode — active profile badge: always LIVE (real network monitoring)
    @GetMapping("/mode")
    public Map<String, String> getMode() {
        return Map.of(
                "profile", "real",
                "label",   "🟢 LIVE"
        );
    }

    // GET /api/stats/timeline?minutes=60 — minute-by-minute replay snapshots + events
    @GetMapping("/timeline")
    public Map<String, Object> getTimelineReplay(
            @RequestParam(defaultValue = "60") int minutes
    ) {
        return incidentTimelineService.buildTimeline(minutes);
    }
}
