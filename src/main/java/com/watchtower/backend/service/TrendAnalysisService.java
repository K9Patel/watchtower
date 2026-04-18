package com.watchtower.backend.service;

import com.watchtower.backend.entity.UsageLog;
import com.watchtower.backend.repository.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AJT — Linear Regression on last 30 readings.
 *
 * Uses Ordinary Least Squares (OLS) formula:
 *   slope = (n·Σxy − Σx·Σy) / (n·Σx² − (Σx)²)
 *   intercept = (Σy − slope·Σx) / n
 *
 * x = reading index (0..29), y = bandwidthPercentage
 * Predicts bandwidth at x = n (the NEXT reading).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrendAnalysisService {

    private final UsageLogRepository usageLogRepository;

    /**
     * Compute OLS linear regression slope, intercept, and predicted next value.
     * @return Map with: slope, intercept, predictedNext, trendLabel, dataPoints
     */
    public Map<String, Object> getTrendAnalysis() {
        List<UsageLog> top30 = usageLogRepository
                .findTop30ForTrend(PageRequest.of(0, 30));

        Map<String, Object> result = new LinkedHashMap<>();

        if (top30.size() < 5) {
            result.put("slope", 0.0);
            result.put("intercept", 0.0);
            result.put("predictedNext", 0.0);
            result.put("trendLabel", "INSUFFICIENT_DATA");
            result.put("dataPointCount", top30.size());
            return result;
        }

        int n = top30.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        // Reverse so index 0 = oldest, index n-1 = newest
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = top30.get(n - 1 - i).getBandwidthPercentage();
            sumX  += x;
            sumY  += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        double slope     = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        double intercept = (sumY - slope * sumX) / n;
        double predicted = Math.min(100.0, Math.max(0.0, intercept + slope * n));

        slope     = Math.round(slope     * 10000.0) / 10000.0;
        intercept = Math.round(intercept * 100.0)   / 100.0;
        predicted = Math.round(predicted * 100.0)   / 100.0;

        String trendLabel;
        if      (slope >  1.5) trendLabel = "RISING_FAST";
        else if (slope >  0.3) trendLabel = "RISING";
        else if (slope < -1.5) trendLabel = "FALLING_FAST";
        else if (slope < -0.3) trendLabel = "FALLING";
        else                   trendLabel = "STABLE";

        result.put("slope",          slope);
        result.put("intercept",      intercept);
        result.put("predictedNext",  predicted);
        result.put("trendLabel",     trendLabel);
        result.put("dataPointCount", n);

        log.debug("TrendAnalysis: slope={} predicted={}% label={}", slope, predicted, trendLabel);
        return result;
    }
}
