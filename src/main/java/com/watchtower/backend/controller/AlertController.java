package com.watchtower.backend.controller;

import com.watchtower.backend.entity.Alert;
import com.watchtower.backend.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AlertController {

    private final AlertRepository alertRepository;

    // GET /api/alerts — unresolved alerts, CRITICAL first (dashboard feed)
    @GetMapping
    @Transactional(readOnly = true)
    public List<Alert> getActiveAlerts() {
        return alertRepository.findByIsResolvedFalseOrderByCreatedAtDesc();
    }

    // GET /api/alerts/all?page=0&size=20 — paginated full history
    @GetMapping("/all")
    public Page<Alert> getAllAlerts(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return alertRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    // GET /api/alerts/count — badge number in navbar
    @GetMapping("/count")
    public Map<String, Long> getUnresolvedCount() {
        return Map.of("unresolved", alertRepository.countByIsResolvedFalse());
    }

    // GET /api/alerts/severity/{level} — filter by LOW|MEDIUM|HIGH|CRITICAL
    @GetMapping("/severity/{level}")
    public ResponseEntity<List<Alert>> getBySeverity(@PathVariable String level) {
        try {
            Alert.Severity severity = Alert.Severity.valueOf(level.toUpperCase());
            return ResponseEntity.ok(alertRepository.findBySeverity(severity));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // PUT /api/alerts/{id}/resolve — mark single alert as resolved
    @PutMapping("/{id}/resolve")
    public ResponseEntity<Map<String, String>> resolveAlert(@PathVariable Long id) {
        return alertRepository.findById(id).map(alert -> {
            alert.setIsResolved(true);
            alertRepository.save(alert);
            return ResponseEntity.ok(Map.of("message", "Alert resolved", "id", id.toString()));
        }).orElse(ResponseEntity.notFound().build());
    }

    // PUT /api/alerts/resolve-all — bulk-resolve all open alerts
    @PutMapping("/resolve-all")
    public Map<String, String> resolveAll() {
        List<Alert> open = alertRepository.findByIsResolvedFalseOrderByCreatedAtDesc();
        open.forEach(a -> a.setIsResolved(true));
        alertRepository.saveAll(open);
        return Map.of("message", "All " + open.size() + " alerts resolved");
    }

    // DELETE /api/alerts/{id} — delete single alert record
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteAlert(@PathVariable Long id) {
        if (!alertRepository.existsById(id)) return ResponseEntity.notFound().build();
        alertRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Alert deleted"));
    }
}
