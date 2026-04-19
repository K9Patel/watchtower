package com.watchtower.backend.service;

import com.watchtower.backend.entity.UsageLog;
import com.watchtower.backend.repository.DeviceRepository;
import com.watchtower.backend.repository.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptive hybrid forecasting for network load.
 *
 * Combines:
 *   1) OLS linear regression (trend direction)
 *   2) Adaptive EMA (fast + slow smoothing)
 *   3) Momentum term from recent deltas
 *
 * Also emits:
 *   - confidence score
 *   - anomaly probability (z-score based)
 *   - per-device next-step forecasts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrendAnalysisService {

    private final UsageLogRepository usageLogRepository;
     private final DeviceRepository deviceRepository;

    public Map<String, Object> getTrendAnalysis() {
        List<UsageLog> recentLogs = usageLogRepository
                .findTop30ForTrend(PageRequest.of(0, 30));

        Map<String, Object> result = new LinkedHashMap<>();

        if (recentLogs.size() < 5) {
            result.put("slope", 0.0);
            result.put("intercept", 0.0);
            result.put("predictedNext", 0.0);
            result.put("trendLabel", "INSUFFICIENT_DATA");
            result.put("dataPointCount", recentLogs.size());
            result.put("model", "ADAPTIVE_HYBRID_V1");
            result.put("confidence", 0.0);
            result.put("anomalyProbability", 0.0);
            result.put("volatility", 0.0);
            result.put("perDeviceForecasts", List.of());
            return result;
        }

        List<UsageLog> orderedLogs = new ArrayList<>(recentLogs);
        orderedLogs.sort(Comparator.comparing(UsageLog::getTimestamp)); // oldest -> newest

        int n = orderedLogs.size();
        double[] values = orderedLogs.stream()
                .mapToDouble(UsageLog::getBandwidthPercentage)
                .toArray();

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        // OLS baseline model
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = values[i];
            sumX  += x;
            sumY  += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        double denominator = (n * sumX2 - sumX * sumX);
        double slope = denominator == 0 ? 0.0 : (n * sumXY - sumX * sumY) / denominator;
        double intercept = (sumY - slope * sumX) / n;

        double regressionPrediction = clamp(intercept + slope * n, 0.0, 100.0);

        // Adaptive smoothing: tune alpha by recent volatility
        double recentStd = stdDev(values);
        double adaptiveAlphaFast = clamp(0.35 + (recentStd / 100.0), 0.25, 0.6);
        double adaptiveAlphaSlow = clamp(0.18 + (recentStd / 200.0), 0.12, 0.35);
        double emaFast = ema(values, adaptiveAlphaFast);
        double emaSlow = ema(values, adaptiveAlphaSlow);

        // Momentum term from last few deltas
        int momentumWindow = Math.min(6, n - 1);
        double momentum = 0.0;
        for (int i = n - momentumWindow; i < n; i++) {
            momentum += (values[i] - values[i - 1]);
        }
        momentum = momentumWindow > 0 ? momentum / momentumWindow : 0.0;

        double momentumPrediction = clamp(values[n - 1] + momentum, 0.0, 100.0);
        double smoothedPrediction = clamp((0.65 * emaFast) + (0.35 * emaSlow), 0.0, 100.0);

        // Adaptive blending: strong linear fit -> trust regression more
        double r2 = computeR2(values, slope, intercept);
        double regressionWeight = clamp(0.25 + (0.55 * r2), 0.25, 0.8);

        double hybridRaw = (0.75 * smoothedPrediction) + (0.25 * momentumPrediction);
        double predicted = clamp(
                (regressionWeight * regressionPrediction) + ((1.0 - regressionWeight) * hybridRaw),
                0.0,
                100.0
        );

        // Confidence combines: fit quality (r2), sample sufficiency, and volatility penalty
        double sampleScore = clamp((n - 4) / 26.0, 0.0, 1.0);
        double volatilityPenalty = clamp(recentStd / 40.0, 0.0, 1.0);
        double confidence = clamp((0.55 * r2) + (0.35 * sampleScore) + (0.10 * (1.0 - volatilityPenalty)), 0.0, 1.0);

        // Z-score based anomaly probability of latest point
        double mean = sumY / n;
        double std = Math.max(0.0001, recentStd);
        double z = Math.abs((values[n - 1] - mean) / std);
        double anomalyProbability = clamp(sigmoid((z - 1.8) * 1.2), 0.0, 1.0);

        slope     = Math.round(slope     * 10000.0) / 10000.0;
        intercept = Math.round(intercept * 100.0)   / 100.0;
        predicted = Math.round(predicted * 100.0)   / 100.0;
        confidence = Math.round((confidence * 100.0) * 10.0) / 10.0;
        anomalyProbability = Math.round((anomalyProbability * 100.0) * 10.0) / 10.0;
        double roundedVolatility = Math.round(recentStd * 100.0) / 100.0;

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
        result.put("model",          "ADAPTIVE_HYBRID_V1");
        result.put("confidence",     confidence);
        result.put("anomalyProbability", anomalyProbability);
        result.put("volatility",     roundedVolatility);
        result.put("perDeviceForecasts", buildPerDeviceForecasts());

        log.debug("TrendAnalysis[HYBRID]: slope={} pred={} label={} conf={} anomaly={}",
                slope, predicted, trendLabel, confidence, anomalyProbability);
        return result;
    }

    private List<Map<String, Object>> buildPerDeviceForecasts() {
        return deviceRepository.findByIsActiveTrue().stream()
                .map(device -> {
                    List<UsageLog> logs = usageLogRepository.findTop100ByDeviceOrderByTimestampDesc(device);
                    if (logs.size() < 5) {
                        return null;
                    }

                    logs.sort(Comparator.comparing(UsageLog::getTimestamp));
                    int n = logs.size();
                    double[] values = logs.stream().mapToDouble(UsageLog::getBandwidthPercentage).toArray();

                    double emaPrediction = ema(values, 0.35);
                    double slope = (values[n - 1] - values[Math.max(0, n - 5)]) / Math.min(4.0, n - 1.0);
                    double predicted = clamp((0.75 * emaPrediction) + (0.25 * (values[n - 1] + slope)), 0.0, 100.0);
                    double volatility = stdDev(values);
                    double confidence = clamp((n / 100.0) * (1.0 - clamp(volatility / 45.0, 0.0, 1.0)), 0.0, 1.0);

                    String trend;
                    if      (slope >  1.5) trend = "RISING_FAST";
                    else if (slope >  0.3) trend = "RISING";
                    else if (slope < -1.5) trend = "FALLING_FAST";
                    else if (slope < -0.3) trend = "FALLING";
                    else                   trend = "STABLE";

                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("deviceId", device.getId());
                    m.put("deviceName", device.getDeviceName());
                    m.put("predictedNext", Math.round(predicted * 100.0) / 100.0);
                    m.put("trendLabel", trend);
                    m.put("confidence", Math.round((confidence * 100.0) * 10.0) / 10.0);
                    m.put("sampleCount", n);
                    return m;
                })
                .filter(m -> m != null)
                .toList();
    }

    private double ema(double[] values, double alpha) {
        if (values.length == 0) return 0.0;
        double out = values[0];
        for (int i = 1; i < values.length; i++) {
            out = alpha * values[i] + (1.0 - alpha) * out;
        }
        return out;
    }

    private double computeR2(double[] values, double slope, double intercept) {
        int n = values.length;
        if (n == 0) return 0.0;

        double mean = 0.0;
        for (double v : values) mean += v;
        mean /= n;

        double ssTot = 0.0;
        double ssRes = 0.0;
        for (int i = 0; i < n; i++) {
            double y = values[i];
            double yHat = intercept + (slope * i);
            ssTot += Math.pow(y - mean, 2);
            ssRes += Math.pow(y - yHat, 2);
        }

        if (ssTot <= 0.0001) return 0.0;
        return clamp(1.0 - (ssRes / ssTot), 0.0, 1.0);
    }

    private double stdDev(double[] values) {
        if (values.length == 0) return 0.0;

        double mean = 0.0;
        for (double v : values) mean += v;
        mean /= values.length;

        double variance = 0.0;
        for (double v : values) {
            variance += Math.pow(v - mean, 2);
        }
        variance /= values.length;
        return Math.sqrt(variance);
    }

    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
