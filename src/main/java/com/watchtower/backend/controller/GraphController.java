package com.watchtower.backend.controller;

import com.watchtower.backend.service.GraphAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for the Attack Path Graph feature.
 *
 * GET /api/graph/attack-path → returns the full risk graph JSON
 * (nodes, edges, criticalPath, summary)
 */
@RestController
@RequestMapping("/api/graph")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class GraphController {

    private final GraphAnalysisService graphAnalysisService;

    /**
     * GET /api/graph/attack-path
     *
     * Returns a directed risk graph showing:
     *   - nodes: devices with risk scores and levels
     *   - edges: lateral movement / correlated activity paths
     *   - criticalPath: ordered list of highest-risk path node IDs
     *   - summary: human-readable narrative
     *
     * Cached for 30 seconds, recalculated on next request after TTL.
     */
    @GetMapping("/attack-path")
    public ResponseEntity<Map<String, Object>> getAttackPathGraph() {
        return ResponseEntity.ok(graphAnalysisService.getAttackPathGraph());
    }
}
