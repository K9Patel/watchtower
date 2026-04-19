package com.watchtower.backend.controller;

import com.watchtower.backend.service.NetworkTopologyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/topology")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class TopologyController {

    private final NetworkTopologyService networkTopologyService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getTopology() {
        return ResponseEntity.ok(networkTopologyService.getTopology());
    }
}
