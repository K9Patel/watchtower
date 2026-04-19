package com.watchtower.backend.controller;

import com.watchtower.backend.service.NetworkTopologyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/topology")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class TopologyController {

    private final NetworkTopologyService networkTopologyService;

    public record ManualGatewayLocationRequest(Double latitude, Double longitude, String addressLabel) {
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getTopology() {
        return ResponseEntity.ok(networkTopologyService.getTopology());
    }

    @PostMapping("/gateway/location/manual")
    public ResponseEntity<Map<String, Object>> setGatewayManualLocation(@RequestBody ManualGatewayLocationRequest request) {
        try {
            if (request == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "updated", false,
                "message", "Request body is required."
            ));
            }
            return ResponseEntity.ok(networkTopologyService.saveGatewayManualLocation(
                    request.latitude(),
                    request.longitude(),
                    request.addressLabel()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "updated", false,
                    "message", e.getMessage()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "updated", false,
                    "message", e.getMessage()
            ));
        }
    }
}
