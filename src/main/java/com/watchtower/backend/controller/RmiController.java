package com.watchtower.backend.controller;

//import com.watchtower.backend.rmi.DiagnosisClient;
//import com.watchtower.backend.rmi.DiagnosisRemote;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

/**
 * AJT — RMI REST Bridge [DEPRECATED - DEAD CODE]
 * This class uses RMI which is no longer required.
 * The functionality is replaced by REST endpoints (StatsController, AnalysisService).
 * Disabled to prevent Spring bean initialization errors.
 */
//@RestController
//@RequestMapping("/api/rmi")
//@CrossOrigin(origins = "*")
public class RmiController {

    /*
    private DiagnosisRemote getRemote() {
        return DiagnosisClient.connect();
    }

    @GetMapping("/load")
    public ResponseEntity<Map<String, Object>> getLoad() {
        DiagnosisRemote remote = getRemote();
        if (remote == null) return rmiUnavailable();
        try {
            return ResponseEntity.ok(Map.of("totalLoad", remote.getTotalLoad(), "source", "RMI"));
        } catch (RemoteException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "RMI call failed: " + e.getMessage()));
        }
    }

    @GetMapping("/alerts")
    public ResponseEntity<Map<String, Object>> getAlertCount() {
        DiagnosisRemote remote = getRemote();
        if (remote == null) return rmiUnavailable();
        try {
            return ResponseEntity.ok(Map.of(
                    "unresolvedAlerts", remote.getUnresolvedAlertCount(), "source", "RMI"));
        } catch (RemoteException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "RMI call failed: " + e.getMessage()));
        }
    }

    @GetMapping("/report")
    public ResponseEntity<List<Map<String, String>>> getDiagnosisReport() {
        DiagnosisRemote remote = getRemote();
        if (remote == null) return ResponseEntity.status(503).build();
        try {
            return ResponseEntity.ok(remote.getLastDiagnosisReport());
        } catch (RemoteException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private ResponseEntity<Map<String, Object>> rmiUnavailable() {
        return ResponseEntity.status(503)
                .body(Map.of("error", "RMI server not reachable on localhost:1099"));
    }
    */
}
