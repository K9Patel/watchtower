package com.watchtower.backend.controller;

import com.watchtower.backend.service.ClusteringService;
import com.watchtower.backend.service.RecommendationService;
import com.watchtower.backend.service.TrendAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AJT REST — Trend + Predictions + Recommendations + Clustering.
 *
 * GET /api/trend/analysis      → linear regression result
 * GET /api/trend/predict       → predicted next bandwidth reading
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

    // Full linear regression analysis
    @GetMapping("/analysis")
    public Map<String, Object> getTrendAnalysis() {
        return trendService.getTrendAnalysis();
    }

    // Single predicted next % (used by dashboard prediction banner)
    @GetMapping("/predict")
    public Map<String, Object> getPredictedNext() {
        Map<String, Object> analysis = trendService.getTrendAnalysis();
        return Map.of(
                "predictedNext", analysis.get("predictedNext"),
                "trendLabel",   analysis.get("trendLabel"),
                "slope",        analysis.get("slope")
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
