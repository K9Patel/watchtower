package com.watchtower.backend.controller;

import com.watchtower.backend.entity.UsageLog;
import com.watchtower.backend.repository.UsageLogRepository;
import com.watchtower.backend.service.HistoryAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/history")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryAnalysisService historyService;
    private final UsageLogRepository usageLogRepository;

    // GET /api/history/daily — last 7 days total MB per day (line chart)
    @GetMapping("/daily")
    public List<Map<String, Object>> getDailyTotals() {
        return historyService.getWeeklyDailyTotals();
    }

    // GET /api/history/health-hourly?minutes=60 — health score trend timeline
    @GetMapping("/health-hourly")
    public List<Map<String, Object>> getHealthHourly(
            @RequestParam(defaultValue = "60") int minutes) {
        return historyService.getHourlyHealthScoreTimeline(minutes);
    }

    // GET /api/history/peak-hours — avg bandwidth % per hour 0-23 (heatmap)
    @GetMapping("/peak-hours")
    public Map<Integer, Double> getPeakHours() {
        return historyService.getPeakHoursLastWeek();
    }

    // GET /api/history/per-device — max/min/avg/total per device (table)
    @GetMapping("/per-device")
    public List<Map<String, Object>> getPerDeviceSummary() {
        return historyService.getPerDeviceSummary();
    }

    // GET /api/history/device/{id}?limit=50 — specific device log history
    @GetMapping("/device/{id}")
    public List<UsageLog> getDeviceHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") int limit) {
        return historyService.getDeviceHistory(id, limit);
    }

    // GET /api/history/feed?page=0&size=30 — paginated recent usage logs
    @GetMapping("/feed")
    public Page<UsageLog> getFeed(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "30") int size) {
        return usageLogRepository.findAllByOrderByTimestampDesc(PageRequest.of(page, size));
    }
}
