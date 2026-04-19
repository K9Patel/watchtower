package com.watchtower.backend.controller;

import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.UsageLog;
import com.watchtower.backend.repository.DeviceRepository;
import com.watchtower.backend.repository.UsageLogRepository;
import com.watchtower.backend.service.AnalysisService;
import com.watchtower.backend.service.BaselineService;
import com.watchtower.backend.service.DeviceDetailService;
import com.watchtower.backend.service.MacVendorService;
import com.watchtower.backend.service.NetworkDiscoveryService;
import com.watchtower.backend.service.NetworkDiscoveryService.ScanResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/devices")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceRepository       deviceRepository;
    private final AnalysisService        analysisService;
    private final DeviceDetailService    deviceDetailService;
    private final NetworkDiscoveryService networkDiscoveryService;
    private final com.watchtower.backend.service.DashboardWebSocketService webSocketService;
    private final MacVendorService macVendorService;
    private final UsageLogRepository usageLogRepository;
    private final BaselineService baselineService;

    // ==========================================================================
    //  Existing CRUD endpoints — unchanged
    // ==========================================================================

    /** GET /api/devices — all devices */
    @GetMapping
    public List<Device> getAllDevices() {
        return deviceRepository.findAll();
    }

    /** GET /api/devices/active — only is_active=true */
    @GetMapping("/active")
    public List<Device> getActiveDevices() {
        return deviceRepository.findByIsActiveTrue();
    }

    /** GET /api/devices/{id} — single device */
    @GetMapping("/{id}")
    public ResponseEntity<Device> getDevice(@PathVariable Long id) {
        return deviceRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** GET /api/devices/{id}/details — extended real-time details */
    @GetMapping("/{id}/details")
    public ResponseEntity<Map<String, Object>> getDeviceDetails(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(deviceDetailService.getDeviceDetails(id));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** GET /api/devices/count */
    @GetMapping("/count")
    public Map<String, Long> getCount() {
        return Map.of("total", deviceRepository.count());
    }

    /** POST /api/devices — create a device manually */
    @PostMapping
    public ResponseEntity<Device> createDevice(@RequestBody Device device) {
        device.setIsAutoDiscovered(false); // manually added → never auto-deleted
        Device saved = deviceRepository.save(device);
        webSocketService.pushDeviceUpdate(saved);
        return ResponseEntity.ok(saved);
    }

    /** PUT /api/devices/{id}/status — disabled: status is discovery-managed */
    @PutMapping("/{id}/status")
    public ResponseEntity<Device> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return ResponseEntity.status(405).build();
    }

    /**
     * GET /api/devices/live
     * All devices enriched with real-time bandwidth share.
     * Sorted: auto-discovered NEW devices first, then ONLINE, then OFFLINE by bandwidth.
     */
    @GetMapping("/live")
    public List<Map<String, Object>> getLiveDevices() {
        List<Device> allDevices = deviceRepository.findByIsActiveTrue();

        Map<Long, Double> mbpsByDeviceId = new HashMap<>();
        double totalMbps = 0.0;

        for (Device device : allDevices) {
            Optional<UsageLog> latestLog = usageLogRepository.findTopByDeviceOrderByTimestampDesc(device);
            double bandwidthMbps = latestLog.map(log -> (log.getBytesUsed() * 8.0) / 10.0).orElse(0.0);
            mbpsByDeviceId.put(device.getId(), bandwidthMbps);
            totalMbps += bandwidthMbps;
        }
        final double totalMbpsSnapshot = totalMbps;

        return allDevices.stream()
                .map(device -> {
                    double bandwidthMbps = mbpsByDeviceId.getOrDefault(device.getId(), 0.0);
                    double bandwidthPercent = totalMbpsSnapshot > 0
                        ? Math.round((bandwidthMbps / totalMbpsSnapshot) * 10000.0) / 100.0
                            : 0.0;

                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("id",               device.getId());
                    info.put("deviceName",        device.getDeviceName());
                    info.put("ipAddress",         device.getIpAddress());
                    info.put("macAddress",        device.getMacAddress());
                    info.put("vendorName",        device.getVendorName());
                    info.put("osType",            device.getOsType());
                    info.put("status",            device.getStatus());
                    info.put("isActive",          device.getIsActive());
                    info.put("isAutoDiscovered",  device.getIsAutoDiscovered());
                    info.put("lastSeenAt",        device.getLastSeenAt());
                    info.put("registeredAt",      device.getRegisteredAt());
                    info.put("baselineReady",     device.getBaselineReady());
                    info.put("baselineSince",     device.getBaselineSince());
                    info.put("bandwidth",         bandwidthPercent);
                    info.put("bandwidthMbps",     Math.round(bandwidthMbps * 100.0) / 100.0);
                    return info;
                })
                .sorted((a, b) -> {
                    String aStatus = (String) a.get("status");
                    String bStatus = (String) b.get("status");
                    if (!aStatus.equals(bStatus)) return "ONLINE".equals(aStatus) ? -1 : 1;
                    Double bandA = (Double) a.get("bandwidth");
                    Double bandB = (Double) b.get("bandwidth");
                    return bandB.compareTo(bandA);
                })
                .collect(Collectors.toList());
    }

    /** DELETE /api/devices/{id} — disabled: devices are discovery-managed */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deactivateDevice(@PathVariable Long id) {
        return ResponseEntity.status(405).body(Map.of(
                "message", "Manual delete is disabled. Devices are managed by live discovery."
        ));
    }

    /**
     * GET /api/devices/{id}/baseline
     * Returns per-hour baseline data for the device detail page chart overlay.
     * Each entry: { hour, avgBandwidth, stddevBandwidth, sampleCount, upperBound, lowerBound }
     */
    @GetMapping("/{id}/baseline")
    public ResponseEntity<Map<String, Object>> getDeviceBaseline(@PathVariable Long id) {
        return deviceRepository.findById(id).map(device -> {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("deviceId", device.getId());
            response.put("deviceName", device.getDeviceName());
            response.put("baselineReady", device.getBaselineReady());
            response.put("baselineSince", device.getBaselineSince());
            response.put("baselines", baselineService.getBaselineForDevice(id));
            return ResponseEntity.ok(response);
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/devices/vendors
     * Returns all devices with their real MAC address and resolved vendor name.
     * Also includes OUI database stats.
     */
    @GetMapping("/vendors")
    public Map<String, Object> getDeviceVendors() {
        List<Device> allDevices = deviceRepository.findAll();

        List<Map<String, Object>> deviceVendors = allDevices.stream()
                .map(device -> {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("id",          device.getId());
                    info.put("deviceName",  device.getDeviceName());
                    info.put("macAddress",  device.getMacAddress());
                    info.put("ipAddress",   device.getIpAddress());
                    info.put("vendorName",  device.getVendorName());
                    info.put("vendorShort", macVendorService.shortLabel(device.getVendorName()));
                    info.put("status",      device.getStatus());
                    info.put("isAutoDiscovered", device.getIsAutoDiscovered());
                    return info;
                })
                .collect(Collectors.toList());

        return Map.of(
            "devices",       deviceVendors,
            "totalDevices",  allDevices.size(),
            "ouiDbEntries",  macVendorService.getDatabaseSize(),
            "withVendor",    allDevices.stream().filter(d -> d.getVendorName() != null).count(),
            "unknownVendor", allDevices.stream().filter(d -> d.getVendorName() == null).count()
        );
    }

    // ==========================================================================
    //  New: Live Discovery endpoints
    // ==========================================================================

    /**
     * POST /api/devices/scan
     *
     * Triggers an immediate ARP scan in a background thread.
     * Returns instantly with current scan status — the actual results will be
     * available via GET /api/devices/scan/status once the scan completes.
     *
     * Typical scan duration: 5-15 seconds on a home network (254 hosts).
     */
    @PostMapping("/scan")
    public ResponseEntity<Map<String, Object>> triggerScan() {
        if (networkDiscoveryService.isScanRunning()) {
            return ResponseEntity.ok(Map.of(
                "message",  "Scan already in progress",
                "scanning", true
            ));
        }

        networkDiscoveryService.triggerScan();

        return ResponseEntity.ok(Map.of(
            "message",  "Network scan started. Poll GET /api/devices/scan/status for results.",
            "scanning", true,
            "startedAt", LocalDateTime.now().toString()
        ));
    }

    /**
     * GET /api/devices/scan/status
     *
     * Returns the result of the most recent scan, including:
     *   - totalFound, newDevices, updatedDevices, removedStale
     *   - scannedAt timestamp
     *   - network prefix and network change detection
     *   - full device list discovered in that scan
     *   - current DB counts (always fresh)
     */
    @GetMapping("/scan/status")
    public ResponseEntity<Map<String, Object>> getScanStatus() {
        ScanResult last = networkDiscoveryService.getLastResult();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scanning",       networkDiscoveryService.isScanRunning());
        response.put("totalInDatabase", deviceRepository.count());
        response.put("onlineNow",
                deviceRepository.findAll().stream()
                        .filter(d -> "ONLINE".equals(d.getStatus())).count());

        if (last != null) {
            response.put("lastScan", Map.of(
                "scannedAt",       last.scannedAt(),
                "totalFound",      last.totalFound(),
                "newDevices",      last.newDevices(),
                "updatedDevices",  last.updatedDevices(),
                "removedStale",    last.removedStale(),
                "networkPrefix",   last.networkPrefix(),
                "networkChanged",  last.networkChanged()
            ));
            response.put("devices", last.devices());
        } else {
            response.put("lastScan", null);
            response.put("devices", List.of());
            response.put("hint", "No scan has completed yet. POST /api/devices/scan to start one.");
        }

        return ResponseEntity.ok(response);
    }
}
