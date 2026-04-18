package com.watchtower.backend.controller;

import com.watchtower.backend.entity.Device;
import com.watchtower.backend.repository.DeviceRepository;
import com.watchtower.backend.service.AnalysisService;
import com.watchtower.backend.service.DeviceDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/devices")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceRepository deviceRepository;
    private final AnalysisService analysisService;
    private final DeviceDetailService deviceDetailService;

    // GET /api/devices — all 25 devices (used by dashboard table)
    @GetMapping
    public List<Device> getAllDevices() {
        return deviceRepository.findAll();
    }

    // GET /api/devices/active — only is_active=true (SimulatorService source)
    @GetMapping("/active")
    public List<Device> getActiveDevices() {
        return deviceRepository.findByIsActiveTrue();
    }

    // GET /api/devices/{id} — single device detail
    @GetMapping("/{id}")
    public ResponseEntity<Device> getDevice(@PathVariable Long id) {
        return deviceRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // GET /api/devices/{id}/details — extended real-time details
    @GetMapping("/{id}/details")
    public ResponseEntity<Map<String, Object>> getDeviceDetails(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(deviceDetailService.getDeviceDetails(id));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }


    // GET /api/devices/count — total count (dashboard badge)
    @GetMapping("/count")
    public Map<String, Long> getCount() {
        return Map.of("total", deviceRepository.count());
    }

    // POST /api/devices — create a new device
    @PostMapping
    public ResponseEntity<Device> createDevice(@RequestBody Device device) {
        Device saved = deviceRepository.save(device);
        return ResponseEntity.ok(saved);
    }

    // PUT /api/devices/{id}/status — toggle online/offline
    @PutMapping("/{id}/status")
    public ResponseEntity<Device> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return deviceRepository.findById(id).map(device -> {
            device.setStatus(status.toUpperCase());
            return ResponseEntity.ok(deviceRepository.save(device));
        }).orElse(ResponseEntity.notFound().build());
    }

    // GET /api/devices/live — all devices with real-time bandwidth (dynamic discovery)
    @GetMapping("/live")
    public List<Map<String, Object>> getLiveDevices() {
        List<Device> allDevices = deviceRepository.findAll();
        Map<String, Double> bandwidthShare = analysisService.getBandwidthSharePerDevice();
        
        return allDevices.stream()
                .map(device -> {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("id", device.getId());
                    info.put("deviceName", device.getDeviceName());
                    info.put("ipAddress", device.getIpAddress());
                    info.put("macAddress", device.getMacAddress());
                    info.put("status", device.getStatus());
                    info.put("isActive", device.getIsActive());
                    info.put("bandwidth", bandwidthShare.getOrDefault(device.getDeviceName(), 0.0));
                    info.put("registeredAt", device.getRegisteredAt());
                    return info;
                })
                .sorted((a, b) -> {
                    // Sort by bandwidth descending (top consumers first)
                    Double bandA = (Double) a.get("bandwidth");
                    Double bandB = (Double) b.get("bandwidth");
                    return bandB.compareTo(bandA);
                })
                .collect(Collectors.toList());
    }

    // DELETE /api/devices/{id} — soft-delete (set is_active = false)
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deactivateDevice(@PathVariable Long id) {
        return deviceRepository.findById(id).map(device -> {
            device.setIsActive(false);
            deviceRepository.save(device);
            return ResponseEntity.ok(Map.of("message", "Device deactivated"));
        }).orElse(ResponseEntity.notFound().build());
    }
}
