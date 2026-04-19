package com.watchtower.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.watchtower.backend.entity.Alert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Optional LLM-backed recommendation generator.
 * If API key/config is missing or call fails, caller should fall back to rule-based logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRecommendationService {

    private final TrendAnalysisService trendAnalysisService;
    private final ObjectMapper objectMapper;

    @Value("${watchtower.ai.enabled:true}")
    private boolean aiEnabled;

    @Value("${watchtower.ai.base-url:https://api.openai.com/v1/chat/completions}")
    private String aiBaseUrl;

    @Value("${watchtower.ai.model:gpt-4o-mini}")
    private String aiModel;

    @Value("${watchtower.ai.api-key:}")
    private String aiApiKey;

    @Value("${watchtower.ai.timeout-ms:12000}")
    private int timeoutMs;

    @Value("${watchtower.ai.max-tokens:260}")
    private int maxTokens;

    @Value("${watchtower.ai.temperature:0.2}")
    private double temperature;

    public Optional<List<java.util.Map<String, String>>> generateRecommendations(List<Alert> openAlerts) {
        if (!aiEnabled || aiApiKey == null || aiApiKey.isBlank()) {
            return Optional.empty();
        }

        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .build();

            String telemetry = buildTelemetryContext(openAlerts);

            String systemPrompt = "You are a network operations AI assistant for WatchTower. "
                    + "Given real telemetry and active alerts, produce concise remediation suggestions. "
                    + "Return ONLY valid JSON array with objects using keys: "
                    + "alertType, severity, device, advice. "
                    + "Advice must be practical, safe, and under 160 characters.";

            String userPrompt = "Telemetry:\n" + telemetry
                    + "\n\nGenerate up to 4 recommendations ordered by operational priority.";

            String requestBody = objectMapper.createObjectNode()
                    .put("model", aiModel)
                    .put("temperature", temperature)
                    .put("max_tokens", maxTokens)
                    .set("messages", objectMapper.createArrayNode()
                            .add(objectMapper.createObjectNode()
                                    .put("role", "system")
                                    .put("content", systemPrompt))
                            .add(objectMapper.createObjectNode()
                                    .put("role", "user")
                                    .put("content", userPrompt)))
                    .toString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiBaseUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Authorization", "Bearer " + aiApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("AI recommendations: provider returned status {}", response.statusCode());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
                log.warn("AI recommendations: empty message content");
                return Optional.empty();
            }

            String raw = stripCodeFences(contentNode.asText());
            JsonNode parsed = objectMapper.readTree(raw);
            if (!parsed.isArray()) {
                log.warn("AI recommendations: response is not a JSON array");
                return Optional.empty();
            }

            List<java.util.Map<String, String>> out = new ArrayList<>();
            for (JsonNode n : parsed) {
                String alertType = n.path("alertType").asText("AI_ANALYSIS");
                String severity = n.path("severity").asText("INFO");
                String device = n.path("device").asText("Network");
                String advice = n.path("advice").asText("").trim();
                if (advice.isEmpty()) {
                    continue;
                }

                java.util.Map<String, String> rec = new LinkedHashMap<>();
                rec.put("alertType", alertType);
                rec.put("severity", severity);
                rec.put("device", device);
                rec.put("advice", advice);
                rec.put("source", "AI");
                out.add(rec);
            }

            if (out.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(out);
        } catch (Exception e) {
            log.warn("AI recommendations unavailable: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String buildTelemetryContext(List<Alert> openAlerts) {
        String alertsText = openAlerts.stream()
                .limit(8)
                .map(a -> String.format("- [%s/%s] device=%s message=%s",
                        a.getAlertType(),
                        a.getSeverity().name(),
                        a.getDevice() != null ? a.getDevice().getDeviceName() : "unknown",
                        a.getMessage()))
                .collect(Collectors.joining("\n"));

        java.util.Map<String, Object> trend = trendAnalysisService.getTrendAnalysis();
        Object predicted = trend.getOrDefault("predictedNext", "n/a");
        Object label = trend.getOrDefault("trendLabel", "n/a");
        Object slope = trend.getOrDefault("slope", "n/a");

        return "Trend: predictedNext=" + predicted + ", trendLabel=" + label + ", slope=" + slope
                + "\nOpen alerts:\n" + alertsText;
    }

    private String stripCodeFences(String s) {
        String trimmed = s.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }

        String withoutStart = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
        return withoutStart.replaceFirst("\\s*```$", "").trim();
    }
}
