package com.watchtower.backend.service;

import com.watchtower.backend.entity.Alert;
import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.DeviceBaseline;
import com.watchtower.backend.entity.UsageLog;
import com.watchtower.backend.repository.AlertRepository;
import com.watchtower.backend.repository.DeviceBaselineRepository;
import com.watchtower.backend.repository.DeviceRepository;
import com.watchtower.backend.repository.UsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Attack Path Graph Analysis Service
 *
 * Constructs a directed risk graph showing possible lateral movement paths
 * using existing alert data, anomaly scores, and device relationships.
 * No packet inspection — works purely from existing WatchTower data.
 *
 * Node = device with computed risk score (0–100)
 * Edge = directed connection showing risk flow / lateral movement
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphAnalysisService {

    private final DeviceRepository deviceRepository;
    private final AlertRepository alertRepository;
    private final UsageLogRepository usageLogRepository;
    private final DeviceBaselineRepository baselineRepository;

    /** Cache duration for the graph (ms) */
    private static final long CACHE_TTL_MS = 30_000;

    private volatile Map<String, Object> cachedGraph = null;
    private volatile long cachedAt = 0;

    // ══════════════════════════════════════════════════════════════════════════
    //  Public API
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the full attack path graph. Cached for 30 seconds.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAttackPathGraph() {
        long now = System.currentTimeMillis();
        if (cachedGraph != null && (now - cachedAt) < CACHE_TTL_MS) {
            return cachedGraph;
        }

        Map<String, Object> graph = buildRiskGraph();
        cachedGraph = graph;
        cachedAt = now;
        return graph;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Core Graph Builder
    // ══════════════════════════════════════════════════════════════════════════

    private Map<String, Object> buildRiskGraph() {
        LocalDateTime now = LocalDateTime.now();

        // ── 1. Fetch all online devices ──────────────────────────────────────
        List<Device> onlineDevices = deviceRepository.findByIsActiveTrue().stream()
                .filter(d -> "ONLINE".equals(d.getStatus()))
                .toList();

        if (onlineDevices.isEmpty()) {
            return Map.of(
                "generatedAt", now.toString(),
                "nodes", List.of(),
                "edges", List.of(),
                "criticalPath", List.of(),
                "summary", "No online devices detected."
            );
        }

        // ── 2. Fetch unresolved HIGH/CRITICAL alerts from last 30 min ───────
        List<Alert> unresolvedAlerts = alertRepository
                .findByIsResolvedFalseOrderByCreatedAtDesc().stream()
                .filter(a -> a.getCreatedAt().isAfter(now.minusMinutes(30)))
                .filter(a -> a.getSeverity() == Alert.Severity.CRITICAL
                          || a.getSeverity() == Alert.Severity.HIGH)
                .toList();

        // Group alerts by device ID
        Map<Long, List<Alert>> alertsByDevice = unresolvedAlerts.stream()
                .collect(Collectors.groupingBy(a -> a.getDevice().getId()));

        // ── 3. Compute bandwidth consumption per device ─────────────────────
        LocalDateTime sixtySecondsAgo = now.minusSeconds(60);
        List<UsageLog> recentLogs = usageLogRepository.findByTimestampAfter(sixtySecondsAgo);
        double totalBytes = recentLogs.stream().mapToDouble(UsageLog::getBytesUsed).sum();

        Map<Long, Double> bandwidthShare = new HashMap<>();
        if (totalBytes > 0) {
            recentLogs.stream()
                .collect(Collectors.groupingBy(
                    log -> log.getDevice().getId(),
                    Collectors.summingDouble(UsageLog::getBytesUsed)))
                .forEach((devId, bytes) ->
                    bandwidthShare.put(devId, (bytes / totalBytes) * 100.0));
        }

        // ── 4. Build nodes with risk scores ─────────────────────────────────
        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<Long, Map<String, Object>> nodeMap = new LinkedHashMap<>();

        for (Device device : onlineDevices) {
            int riskScore = 0;
            int alertCount = 0;
            boolean isAnomaly = false;
            boolean isBigConsumer = false;

            // +40 for CRITICAL alert
            List<Alert> deviceAlerts = alertsByDevice.getOrDefault(device.getId(), List.of());
            alertCount = deviceAlerts.size();
            if (deviceAlerts.stream().anyMatch(a -> a.getSeverity() == Alert.Severity.CRITICAL)) {
                riskScore += 40;
            }
            // +25 for HIGH alert (not already counted as CRITICAL)
            if (deviceAlerts.stream().anyMatch(a -> a.getSeverity() == Alert.Severity.HIGH)) {
                riskScore += 25;
            }

            // +20 if consuming > 40% bandwidth
            double share = bandwidthShare.getOrDefault(device.getId(), 0.0);
            if (share > 40.0) {
                riskScore += 20;
                isBigConsumer = true;
            }

            // +15 if behavior drift > 2 stddev from baseline
            if (Boolean.TRUE.equals(device.getBaselineReady())) {
                short currentHour = (short) now.getHour();
                Optional<DeviceBaseline> baselineOpt = baselineRepository
                        .findByDeviceIdAndHourOfDay(device.getId(), currentHour);
                if (baselineOpt.isPresent()) {
                    DeviceBaseline baseline = baselineOpt.get();
                    double currentBandwidth = share;
                    double drift = Math.abs(currentBandwidth - baseline.getAvgBandwidth());
                    if (baseline.getStddevBandwidth() > 0 && drift > 2 * baseline.getStddevBandwidth()) {
                        riskScore += 15;
                        isAnomaly = true;
                    }
                }
            }

            riskScore = Math.min(riskScore, 100);

            String riskLevel;
            if (riskScore >= 60) riskLevel = "CRITICAL";
            else if (riskScore >= 40) riskLevel = "HIGH_RISK";
            else if (riskScore >= 20) riskLevel = "SUSPICIOUS";
            else riskLevel = "SAFE";

            String label = device.getDeviceName() != null
                    ? device.getDeviceName()
                    : device.getIpAddress();

            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", device.getId());
            node.put("label", label);
            node.put("ip", device.getIpAddress());
            node.put("vendor", device.getVendorName());
            node.put("osType", device.getOsType());
            node.put("riskScore", riskScore);
            node.put("riskLevel", riskLevel);
            node.put("alertCount", alertCount);
            node.put("isAnomaly", isAnomaly);
            node.put("isBigConsumer", isBigConsumer);
            node.put("bandwidthShare", Math.round(share * 10.0) / 10.0);

            nodes.add(node);
            nodeMap.put(device.getId(), node);
        }

        // ── 5. Build edges ──────────────────────────────────────────────────
        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> edgeKeys = new HashSet<>(); // deduplicate

        // Edge Rule 1: HIGH_RISK/CRITICAL device → any ONLINE device in same subnet
        for (Map<String, Object> node : nodes) {
            String level = (String) node.get("riskLevel");
            if ("HIGH_RISK".equals(level) || "CRITICAL".equals(level)) {
                String sourceIp = (String) node.get("ip");
                String sourceSubnet = extractSubnet(sourceIp);

                for (Map<String, Object> target : nodes) {
                    if (node.get("id").equals(target.get("id"))) continue;
                    String targetIp = (String) target.get("ip");
                    if (sourceSubnet.equals(extractSubnet(targetIp))) {
                        String edgeKey = node.get("id") + "->" + target.get("id") + ":lateral";
                        if (edgeKeys.add(edgeKey)) {
                            Map<String, Object> edge = new LinkedHashMap<>();
                            edge.put("source", node.get("id"));
                            edge.put("target", target.get("id"));
                            edge.put("label", "possible lateral movement");
                            edge.put("riskLevel", level);
                            edges.add(edge);
                        }
                    }
                }
            }
        }

        // Edge Rule 2: Unknown vendor with alert → highest-traffic known device
        Optional<Map<String, Object>> highestTrafficKnown = nodes.stream()
                .filter(n -> n.get("vendor") != null)
                .max(Comparator.comparingDouble(n -> (double) n.get("bandwidthShare")));

        if (highestTrafficKnown.isPresent()) {
            for (Map<String, Object> node : nodes) {
                if (node.get("vendor") == null && (int) node.get("alertCount") > 0) {
                    String edgeKey = node.get("id") + "->" + highestTrafficKnown.get().get("id") + ":unknown";
                    if (edgeKeys.add(edgeKey)) {
                        Map<String, Object> edge = new LinkedHashMap<>();
                        edge.put("source", node.get("id"));
                        edge.put("target", highestTrafficKnown.get().get("id"));
                        edge.put("label", "unknown device → high-value target");
                        edge.put("riskLevel", "CRITICAL");
                        edges.add(edge);
                    }
                }
            }
        }

        // Edge Rule 3: Correlated activity — devices that both spiked within 2 min
        Map<Long, List<UsageLog>> logsByDevice = recentLogs.stream()
                .collect(Collectors.groupingBy(log -> log.getDevice().getId()));

        List<Long> deviceIds = new ArrayList<>(logsByDevice.keySet());
        for (int i = 0; i < deviceIds.size(); i++) {
            for (int j = i + 1; j < deviceIds.size(); j++) {
                Long idA = deviceIds.get(i);
                Long idB = deviceIds.get(j);

                if (!nodeMap.containsKey(idA) || !nodeMap.containsKey(idB)) continue;

                List<UsageLog> logsA = logsByDevice.get(idA);
                List<UsageLog> logsB = logsByDevice.get(idB);

                // Check if any spike (>50% bandwidth) coincides within 2 min
                boolean correlated = false;
                for (UsageLog logA : logsA) {
                    if (logA.getBandwidthPercentage() < 50) continue;
                    for (UsageLog logB : logsB) {
                        if (logB.getBandwidthPercentage() < 50) continue;
                        long diffSeconds = Math.abs(
                            ChronoUnit.SECONDS.between(logA.getTimestamp(), logB.getTimestamp()));
                        if (diffSeconds <= 120) {
                            correlated = true;
                            break;
                        }
                    }
                    if (correlated) break;
                }

                if (correlated) {
                    String edgeKey = Math.min(idA, idB) + "<->" + Math.max(idA, idB) + ":correlated";
                    if (edgeKeys.add(edgeKey)) {
                        Map<String, Object> edge = new LinkedHashMap<>();
                        edge.put("source", idA);
                        edge.put("target", idB);
                        edge.put("label", "correlated activity");
                        edge.put("riskLevel", "SUSPICIOUS");
                        edges.add(edge);
                    }
                }
            }
        }

        // ── 6. Find critical path (highest risk chain) ──────────────────────
        List<Long> criticalPath = findCriticalPath(nodes, edges);

        // ── 7. Build summary narrative ──────────────────────────────────────
        long criticalCount = nodes.stream()
                .filter(n -> "CRITICAL".equals(n.get("riskLevel"))).count();
        long highRiskCount = nodes.stream()
                .filter(n -> "HIGH_RISK".equals(n.get("riskLevel"))).count();

        String summary;
        if (criticalCount == 0 && highRiskCount == 0) {
            summary = "✅ Network is clean. No high-risk devices or suspicious lateral movement paths detected.";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("⚠ %d critical, %d high-risk device(s) detected.", criticalCount, highRiskCount));
            if (!criticalPath.isEmpty()) {
                sb.append(" Likely attack path: ");
                sb.append(criticalPath.stream()
                    .map(id -> {
                        Map<String, Object> n = nodeMap.get(id);
                        return n != null ? n.get("label") + " (" + n.get("ip") + ")" : "Unknown";
                    })
                    .collect(Collectors.joining(" → ")));
            }
            summary = sb.toString();
        }

        // ── 8. Return graph ─────────────────────────────────────────────────
        Map<String, Object> graph = new LinkedHashMap<>();
        graph.put("generatedAt", now.toString());
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        graph.put("criticalPath", criticalPath);
        graph.put("summary", summary);
        graph.put("stats", Map.of(
            "totalNodes", nodes.size(),
            "totalEdges", edges.size(),
            "criticalDevices", criticalCount,
            "highRiskDevices", highRiskCount,
            "safeDevices", nodes.stream().filter(n -> "SAFE".equals(n.get("riskLevel"))).count()
        ));

        log.info("GraphAnalysis: built attack path graph — {} nodes, {} edges, {} critical path nodes",
                nodes.size(), edges.size(), criticalPath.size());

        return graph;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Extract subnet prefix (first 3 octets) from an IP address.
     */
    private String extractSubnet(String ip) {
        if (ip == null) return "";
        String[] parts = ip.split("\\.");
        if (parts.length >= 3) {
            return parts[0] + "." + parts[1] + "." + parts[2];
        }
        return ip;
    }

    /**
     * Find the highest-risk path through the graph using a greedy approach.
     * Starts from the highest-risk node and follows edges to the next highest-risk neighbor.
     */
    private List<Long> findCriticalPath(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        if (nodes.isEmpty()) return List.of();

        // Build adjacency list from edges
        Map<Long, Set<Long>> adjacency = new HashMap<>();
        for (Map<String, Object> edge : edges) {
            Long source = toLong(edge.get("source"));
            Long target = toLong(edge.get("target"));
            adjacency.computeIfAbsent(source, k -> new HashSet<>()).add(target);
        }

        // Sort nodes by risk score descending
        List<Map<String, Object>> sortedNodes = nodes.stream()
                .sorted((a, b) -> Integer.compare((int) b.get("riskScore"), (int) a.get("riskScore")))
                .toList();

        // Start from highest-risk node
        Map<String, Object> startNode = sortedNodes.get(0);
        if ((int) startNode.get("riskScore") == 0) return List.of();

        // Map id → risk score
        Map<Long, Integer> riskScores = nodes.stream()
                .collect(Collectors.toMap(
                    n -> toLong(n.get("id")),
                    n -> (int) n.get("riskScore")));

        // Greedy walk: follow edges to highest-risk unvisited neighbor
        List<Long> path = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Long current = toLong(startNode.get("id"));

        while (current != null && !visited.contains(current)) {
            path.add(current);
            visited.add(current);

            Set<Long> neighbors = adjacency.getOrDefault(current, Set.of());
            current = neighbors.stream()
                    .filter(n -> !visited.contains(n))
                    .max(Comparator.comparingInt(n -> riskScores.getOrDefault(n, 0)))
                    .orElse(null);
        }

        return path.size() > 1 ? path : List.of(); // Only return paths with 2+ nodes
    }

    private Long toLong(Object val) {
        if (val instanceof Long) return (Long) val;
        if (val instanceof Integer) return ((Integer) val).longValue();
        return Long.parseLong(val.toString());
    }
}
