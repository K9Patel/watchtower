package com.watchtower.backend.controller;

import com.watchtower.backend.service.ClusteringService;
import com.watchtower.backend.service.RecommendationService;
import com.watchtower.backend.service.TrendAnalysisService;
import com.watchtower.backend.service.UserDataScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AJT REST — Trend + Predictions + Recommendations + Clustering.
 *
 * GET /api/trend/analysis      → adaptive hybrid trend analysis
 * GET /api/trend/predict       → predicted next bandwidth reading (+ confidence)
 * GET /api/trend/recommend     → prioritised remediation advice
 * GET /api/trend/clusters      → K-Means device group assignments
 */
@RestController
@RequestMapping("/api/trend")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class TrendController {

    private final TrendAnalysisService  trendService;
    private final RecommendationService recommendationService;
    private final ClusteringService     clusteringService;
    private final UserDataScopeService  userDataScopeService;

    // Full adaptive analysis
    @GetMapping("/analysis")
    public Map<String, Object> getTrendAnalysis(Authentication authentication) {
        // Use all available historical data for trend analysis (don't filter by user scope)
        // User scope is for device filtering, not trend history
        return trendService.getTrendAnalysis(null);
    }

    // Prediction subset (used by lightweight clients)
    @GetMapping("/predict")
    public Map<String, Object> getPredictedNext(Authentication authentication) {
        // Use all available historical data for trend analysis (don't filter by user scope)
        Map<String, Object> analysis = trendService.getTrendAnalysis(null);
        return Map.of(
                "predictedNext", analysis.get("predictedNext"),
                "trendLabel",   analysis.get("trendLabel"),
                "slope",        analysis.get("slope"),
                "confidence",   analysis.get("confidence"),
                "model",        analysis.get("model"),
                "dataPointCount", analysis.get("dataPointCount")
        );
    }

    // Recommendation cards for the dashboard advice panel
    @GetMapping("/recommend")
    public List<Map<String, String>> getRecommendations() {
        return recommendationService.getRecommendations();
    }

    // K-Means cluster assignments for device table colour badges
    @GetMapping("/clusters")
    public List<Map<String, Object>> getClusters() {
        return clusteringService.clusterDevices();
    }
}
