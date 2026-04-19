package com.watchtower.backend.controller;

import com.watchtower.backend.entity.UsageLog;
import com.watchtower.backend.entity.UserNetworkScope;
import com.watchtower.backend.repository.UsageLogRepository;
import com.watchtower.backend.service.HistoryAnalysisService;
import com.watchtower.backend.service.UserDataScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/history")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryAnalysisService historyService;
    private final UsageLogRepository usageLogRepository;
    private final UserDataScopeService userDataScopeService;

    // GET /api/history/network-states/previous — all previous network connections for this user
    @GetMapping("/network-states/previous")
    public List<Map<String, Object>> getPreviousNetworkStates(Authentication authentication) {
        String userEmail = authentication != null ? authentication.getName() : null;
        return userDataScopeService.getPreviousScopes(userEmail).stream()
                .map(scope -> Map.<String, Object>of(
                        "networkPrefix", scope.getNetworkPrefix(),
                        "startedAt", scope.getStartedAt(),
                        "lastSeenAt", scope.getLastSeenAt()
                ))
                .toList();
    }

    // GET /api/history/network-state?networkPrefix=x.x.x
    // Returns ONLY the state recorded before this network changed away.
    @GetMapping("/network-state")
    public ResponseEntity<Map<String, Object>> getPreviousNetworkStateSnapshot(
            @RequestParam String networkPrefix,
            Authentication authentication) {
        String userEmail = authentication != null ? authentication.getName() : null;
        var scopeOpt = userDataScopeService.getScopeForNetwork(userEmail, networkPrefix);
        if (scopeOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        UserNetworkScope scope = scopeOpt.get();
        LocalDateTime since = scope.getStartedAt();
        LocalDateTime until = scope.getLastSeenAt();

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("networkPrefix", scope.getNetworkPrefix());
        payload.put("startedAt", since);
        payload.put("lastSeenAt", until);
        payload.put("daily", historyService.getDailyTotalsBetween(since, until));
        payload.put("peakHours", historyService.getPeakHoursBetween(since, until));
        payload.put("perDevice", historyService.getPerDeviceSummaryBetween(since, until));
        return ResponseEntity.ok(payload);
    }

    // GET /api/history/daily — last 7 days total MB per day (line chart)
    @GetMapping("/daily")
    public List<Map<String, Object>> getDailyTotals(Authentication authentication) {
        String userEmail = authentication != null ? authentication.getName() : null;
        LocalDateTime scopeStart = userDataScopeService.getOrCreateScopeStart(userEmail);
        return historyService.getWeeklyDailyTotals(scopeStart);
    }

    // GET /api/history/peak-hours — avg bandwidth % per hour 0-23 (heatmap)
    @GetMapping("/peak-hours")
    public Map<Integer, Double> getPeakHours(Authentication authentication) {
        String userEmail = authentication != null ? authentication.getName() : null;
        LocalDateTime scopeStart = userDataScopeService.getOrCreateScopeStart(userEmail);
        return historyService.getPeakHoursLastWeek(scopeStart);
    }

    // GET /api/history/per-device — max/min/avg/total per device (table)
    @GetMapping("/per-device")
    public List<Map<String, Object>> getPerDeviceSummary(Authentication authentication) {
        String userEmail = authentication != null ? authentication.getName() : null;
        LocalDateTime scopeStart = userDataScopeService.getOrCreateScopeStart(userEmail);
        return historyService.getPerDeviceSummary(scopeStart);
    }

    // GET /api/history/device/{id}?limit=50 — specific device log history
    @GetMapping("/device/{id}")
    public List<UsageLog> getDeviceHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication) {
        String userEmail = authentication != null ? authentication.getName() : null;
        LocalDateTime scopeStart = userDataScopeService.getOrCreateScopeStart(userEmail);
        return historyService.getDeviceHistory(id, limit, scopeStart);
    }

    // GET /api/history/feed?page=0&size=30 — paginated recent usage logs
    @GetMapping("/feed")
    public Page<UsageLog> getFeed(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "30") int size,
            Authentication authentication) {
        String userEmail = authentication != null ? authentication.getName() : null;
        LocalDateTime scopeStart = userDataScopeService.getOrCreateScopeStart(userEmail);
        return usageLogRepository.findByTimestampAfterOrderByTimestampDesc(scopeStart, PageRequest.of(page, size));
    }
}
