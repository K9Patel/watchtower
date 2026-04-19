package com.watchtower.backend.controller;

import com.watchtower.backend.dto.DpiDeviceDto;
import com.watchtower.backend.dto.DpiTrafficEntryDto;
import com.watchtower.backend.dto.ServiceBreakdownDto;
import com.watchtower.backend.service.DpiQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dpi")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class DpiController {

    private final DpiQueryService dpiQueryService;

    @GetMapping("/device/{id}")
    public ResponseEntity<DpiDeviceDto> getDeviceSnapshot(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(dpiQueryService.getDeviceSnapshot(id));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/device/{id}/history")
    public ResponseEntity<List<DpiTrafficEntryDto>> getDeviceHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1h") String range) {
        try {
            return ResponseEntity.ok(dpiQueryService.getDeviceHistory(id, range));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/breakdown")
    public List<ServiceBreakdownDto> getBreakdown(
            @RequestParam(defaultValue = "1h") String range) {
        return dpiQueryService.getBreakdown(range);
    }

    @GetMapping("/weekly")
    public Map<String, Object> getWeeklySummary() {
        return dpiQueryService.getWeeklySummary();
    }
}
