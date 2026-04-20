package com.watchtower.backend.controller;

import com.watchtower.backend.service.NetworkSpeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * API endpoints for network interface speed detection.
 */
@RestController
@RequestMapping("/api/network")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class NetworkSpeedController {

    private final NetworkSpeedService networkSpeedService;

    /**
     * GET /api/network/speed
     * Returns the active interface speed (WiFi or Ethernet).
     * 
     * Response:
     * {
     *   "timestamp": "2026-04-20T09:00:00...",
     *   "speedMbps": 433,
     *   "speedGbps": 0.433,
     *   "status": "active",
     *   "interfaceType": "5GHz WiFi (AC/AX)"
     * }
     */
    @GetMapping("/speed")
    public Map<String, Object> getActiveInterfaceSpeed() {
        return networkSpeedService.getActiveInterfaceSpeed();
    }

    /**
     * GET /api/network/interfaces
     * Returns all available network interfaces and their speeds.
     * 
     * Response:
     * [
     *   {
     *     "name": "eth0",
     *     "displayName": "WiFi Adapter",
     *     "hardwareAddress": "A1:B2:C3:D4:E5:F6",
     *     "mtu": 1500,
     *     "speedMbps": 433,
     *     "interfaceType": "5GHz WiFi (AC/AX)"
     *   },
     *   ...
     * ]
     */
    @GetMapping("/interfaces")
    public List<Map<String, Object>> getAllInterfacesSpeeds() {
        return networkSpeedService.getAllInterfacesSpeeds();
    }
}
