package com.watchtower.backend.rmi;

import lombok.extern.slf4j.Slf4j;

import java.rmi.Naming;
import java.util.List;
import java.util.Map;

/**
 * AJT — RMI: Client (Naming.lookup).
 *
 * This class demonstrates calling the remote DiagnosisServer from a completely
 * separate JVM process. In WatchTower, RmiController.java calls these methods
 * from within Spring (same JVM) as a demonstration of the RMI API.
 *
 * To run as a standalone client from command line:
 *   java -cp watchtower-backend.jar com.watchtower.backend.rmi.DiagnosisClient
 */
@Slf4j
public class DiagnosisClient {

    private static final String RMI_URL = "rmi://localhost:1099/WatchTowerDiagnosis";

    /**
     * Looks up the remote DiagnosisRemote stub via RMI registry.
     * Returns null if the server is not reachable.
     */
    public static DiagnosisRemote connect() {
        try {
            DiagnosisRemote remote = (DiagnosisRemote) Naming.lookup(RMI_URL);
            log.info("DiagnosisClient: connected to {}", RMI_URL);
            return remote;
        } catch (Exception e) {
            log.error("DiagnosisClient: could not connect to RMI server — {}", e.getMessage());
            return null;
        }
    }

    /** Standalone entry point — runs a quick demo of all remote methods. */
    public static void main(String[] args) {
        DiagnosisRemote remote = connect();
        if (remote == null) {
            System.err.println("RMI server not available. Start WatchTower backend first.");
            return;
        }

        try {
            System.out.println("=== RMI DiagnosisClient Demo ===");
            System.out.printf("Total Network Load : %.2f%%%n", remote.getTotalLoad());
            System.out.printf("Unresolved Alerts  : %d%n",     remote.getUnresolvedAlertCount());

            System.out.println("--- Last Diagnosis Report ---");
            List<Map<String, String>> report = remote.getLastDiagnosisReport();
            if (report.isEmpty()) {
                System.out.println("  No active alerts.");
            } else {
                report.forEach(entry -> System.out.printf(
                        "  [%s] %s on %s — %s%n",
                        entry.get("severity"), entry.get("type"),
                        entry.get("device"),   entry.get("message")));
            }

        } catch (Exception e) {
            System.err.println("RMI call failed: " + e.getMessage());
        }
    }
}
