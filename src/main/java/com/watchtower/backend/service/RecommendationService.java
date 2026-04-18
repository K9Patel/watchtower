package com.watchtower.backend.service;

import com.watchtower.backend.entity.Alert;
import com.watchtower.backend.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AJT — Rule-based Recommendation Engine.
 *
 * Maps (alertType, severity) combinations to plain-English remediation advice.
 * Called by TrendController and displayed on the dashboard "Advice" panel.
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final AlertRepository alertRepository;

    // Keyed by "ALERT_TYPE:SEVERITY"
    private static final Map<String, String> RULE_MAP = new LinkedHashMap<>();

    static {
        RULE_MAP.put("CONGESTION:CRITICAL",          "Immediately throttle all non-essential traffic. Contact your ISP.");
        RULE_MAP.put("CONGESTION:HIGH",              "Enable QoS and prioritise critical services. Limit streaming.");
        RULE_MAP.put("CONGESTION:MEDIUM",            "Monitor bandwidth usage. Consider scheduling large downloads off-peak.");
        RULE_MAP.put("SPIKE:HIGH",                   "Investigate recent large downloads. Block torrent clients if detected.");
        RULE_MAP.put("SPIKE:MEDIUM",                 "Check for software updates or backups running. Reschedule to off-peak.");
        RULE_MAP.put("STREAMING_OVERLOAD:MEDIUM",    "Implement streaming rate limits per device. Enforce AUP on STUDENT devices.");
        RULE_MAP.put("ANOMALY:CRITICAL",             "URGENT: Possible network intrusion or rogue device. Isolate and investigate.");
        RULE_MAP.put("ANOMALY:HIGH",                 "Unusual statistical behaviour detected. Review device logs and user activity.");
        RULE_MAP.put("DEFAULT",                      "Network is operating within normal parameters. No action required.");
    }

    /**
     * Returns a prioritised list of recommendations based on open alerts.
     */
    public List<Map<String, String>> getRecommendations() {
        List<Alert> openAlerts = alertRepository.findByIsResolvedFalseOrderBySeverityDesc();

        if (openAlerts.isEmpty()) {
            return List.of(Map.of(
                    "alertType", "NONE",
                    "severity",  "OK",
                    "advice",    RULE_MAP.get("DEFAULT")));
        }

        List<Map<String, String>> recommendations = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Alert alert : openAlerts) {
            String key = alert.getAlertType() + ":" + alert.getSeverity().name();
            if (seen.contains(key)) continue;
            seen.add(key);

            String advice = RULE_MAP.getOrDefault(key,
                    RULE_MAP.getOrDefault(alert.getAlertType() + ":MEDIUM",
                            RULE_MAP.get("DEFAULT")));

            recommendations.add(Map.of(
                    "alertType",  alert.getAlertType(),
                    "severity",   alert.getSeverity().name(),
                    "device",     alert.getDevice().getDeviceName(),
                    "advice",     advice));
        }

        return recommendations;
    }
}
