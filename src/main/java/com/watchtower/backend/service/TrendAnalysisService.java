package com.watchtower.backend.service;

import com.watchtower.backend.entity.UsageLog;
import com.watchtower.backend.repository.DeviceRepository;
import com.watchtower.backend.repository.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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

    private static final int TREND_WINDOW = 120;

    private final UsageLogRepository usageLogRepository;
    private final DeviceRepository deviceRepository;

    public Map<String, Object> getTrendAnalysis() {
        List<UsageLog> recentLogs = usageLogRepository
                .findTop30ForTrend(PageRequest.of(0, TREND_WINDOW));

        Map<String, Object> result = new LinkedHashMap<>();

        if (recentLogs.size() < 5) {
            result.put("slope", 0.0);
            result.put("intercept", 0.0);
            result.put("predictedNext", 0.0);
            result.put("trendLabel", "INSUFFICIENT_DATA");
            result.put("dataPointCount", recentLogs.size());
            result.put("model", "ADAPTIVE_HYBRID_V2");
            result.put("confidence", 0.0);
            result.put("anomalyProbability", 0.0);
            result.put("volatility", 0.0);
            result.put("perDeviceForecasts", List.of());
            result.put("aggregateDeviceConfidence", 0.0);
            return result;
        }

        List<UsageLog> orderedLogs = new ArrayList<>(recentLogs);
        orderedLogs.sort(Comparator.comparing(UsageLog::getTimestamp)); // oldest -> newest

        int n = orderedLogs.size();
        double[] rawValues = orderedLogs.stream()
                .mapToDouble(UsageLog::getBandwidthPercentage)
                .toArray();

        // Robust pre-smoothing: median filter removes spikes, EMA keeps trend continuity.
        double[] values = emaSeries(medianFilter(rawValues, 3), 0.28);

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

        // Segment-aware baseline (work hours vs off-hours) to avoid pessimistic confidence
        // when traffic pattern changes by time of day.
        double segmentMean = segmentMean(orderedLogs, LocalDateTime.now().getHour());

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
        double blendedPrediction = clamp(
                (regressionWeight * regressionPrediction) + ((1.0 - regressionWeight) * hybridRaw),
                0.0,
                100.0
        );
        double predicted = clamp((0.85 * blendedPrediction) + (0.15 * segmentMean), 0.0, 100.0);

        // Confidence combines: fit quality, sample sufficiency, trend consistency,
        // and a softer volatility penalty.
        double sampleScore = clamp((n - 4) / (double) (TREND_WINDOW - 4), 0.0, 1.0);
        double trendConsistency = trendConsistency(values);
        double volatilityPenalty = clamp(recentStd / 55.0, 0.0, 1.0);
        double modelConfidence = clamp(
            (0.45 * r2)
                + (0.25 * sampleScore)
                + (0.20 * trendConsistency)
                + (0.10 * (1.0 - volatilityPenalty)),
            0.0,
            1.0
        );

        List<Map<String, Object>> perDeviceForecasts = buildPerDeviceForecasts();
        double aggregateDeviceConfidence = aggregateWeightedDeviceConfidence(perDeviceForecasts);

        // Blend model confidence with traffic-weighted per-device confidence.
        double confidence = clamp((0.7 * modelConfidence) + (0.3 * aggregateDeviceConfidence), 0.0, 1.0);

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
        result.put("model",          "ADAPTIVE_HYBRID_V2");
        result.put("confidence",     confidence);
        result.put("anomalyProbability", anomalyProbability);
        result.put("volatility",     roundedVolatility);
        result.put("perDeviceForecasts", perDeviceForecasts);
        result.put("aggregateDeviceConfidence", Math.round((aggregateDeviceConfidence * 100.0) * 10.0) / 10.0);

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
                    double confidence = clamp((n / 120.0) * (1.0 - clamp(volatility / 55.0, 0.0, 1.0)), 0.0, 1.0);
                    double trafficWeight = Math.max(0.2, values[n - 1]);

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
                    m.put("trafficWeight", Math.round(trafficWeight * 100.0) / 100.0);
                    return m;
                })
                .filter(m -> m != null)
                .toList();
    }

    private double aggregateWeightedDeviceConfidence(List<Map<String, Object>> forecasts) {
        if (forecasts == null || forecasts.isEmpty()) return 0.0;

        double weighted = 0.0;
        double totalWeight = 0.0;

        for (Map<String, Object> f : forecasts) {
            double cPct = toDouble(f.get("confidence"));
            double c = clamp(cPct / 100.0, 0.0, 1.0);
            double w = Math.max(0.2, toDouble(f.get("trafficWeight")));
            weighted += c * w;
            totalWeight += w;
        }

        if (totalWeight <= 0.0001) return 0.0;
        return clamp(weighted / totalWeight, 0.0, 1.0);
    }

    private double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try {
            return value == null ? 0.0 : Double.parseDouble(value.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double segmentMean(List<UsageLog> logs, int currentHour) {
        if (logs == null || logs.isEmpty()) return 0.0;

        boolean workSegment = isWorkHour(currentHour);
        List<Double> segmentValues = logs.stream()
                .filter(l -> l.getTimestamp() != null)
                .filter(l -> isWorkHour(l.getTimestamp().getHour()) == workSegment)
                .map(UsageLog::getBandwidthPercentage)
                .toList();

        if (segmentValues.isEmpty()) {
            return logs.stream().mapToDouble(UsageLog::getBandwidthPercentage).average().orElse(0.0);
        }

        return segmentValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private boolean isWorkHour(int hour) {
        return hour >= 8 && hour < 20;
    }

    private double[] medianFilter(double[] values, int window) {
        if (values.length == 0) return values;
        int half = Math.max(1, window / 2);
        double[] out = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            int start = Math.max(0, i - half);
            int end = Math.min(values.length - 1, i + half);
            List<Double> slice = new ArrayList<>();
            for (int j = start; j <= end; j++) {
                slice.add(values[j]);
            }
            slice.sort(Double::compareTo);
            out[i] = slice.get(slice.size() / 2);
        }
        return out;
    }

    private double[] emaSeries(double[] values, double alpha) {
        if (values.length == 0) return values;
        double[] out = new double[values.length];
        out[0] = values[0];
        for (int i = 1; i < values.length; i++) {
            out[i] = alpha * values[i] + (1.0 - alpha) * out[i - 1];
        }
        return out;
    }

    private double trendConsistency(double[] values) {
        if (values.length < 4) return 0.5;
        int consistent = 0;
        int total = 0;
        for (int i = 2; i < values.length; i++) {
            double d1 = values[i - 1] - values[i - 2];
            double d2 = values[i] - values[i - 1];
            if (Math.signum(d1) == Math.signum(d2)) consistent++;
            total++;
        }
        if (total == 0) return 0.5;
        return clamp(consistent / (double) total, 0.0, 1.0);
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
