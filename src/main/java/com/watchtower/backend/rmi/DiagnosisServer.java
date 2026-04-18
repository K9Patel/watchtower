package com.watchtower.backend.rmi;

import com.watchtower.backend.repository.AlertRepository;
import com.watchtower.backend.service.AnalysisService;
import com.watchtower.backend.service.SimulationControlService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AJT — RMI: Server (UnicastRemoteObject).
 *
 * Cannot use @RequiredArgsConstructor here because UnicastRemoteObject requires
 * an explicit no-args-or-one-arg constructor that declares RemoteException.
 * We solve this by injecting via ApplicationContext instead of field injection.
 *
 * @PostConstruct: creates RMI registry on port 1099 and binds itself.
 * @PreDestroy: unbinds cleanly on shutdown.
 */
@Slf4j
@Component
public class DiagnosisServer extends UnicastRemoteObject implements DiagnosisRemote {

    // Injected via setter from ApplicationContext after construction
    private AnalysisService          analysisService;
    private AlertRepository          alertRepository;
    private SimulationControlService controlService;

    private final ApplicationContext applicationContext;
    private Registry registry;

    // Spring calls this constructor — we must declare RemoteException
    public DiagnosisServer(ApplicationContext applicationContext) throws RemoteException {
        super();
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void startRmiServer() {
        // Resolve beans after construction to avoid circular dependency issues
        this.analysisService = applicationContext.getBean(AnalysisService.class);
        this.alertRepository = applicationContext.getBean(AlertRepository.class);
        this.controlService  = applicationContext.getBean(SimulationControlService.class);

        try {
            registry = LocateRegistry.createRegistry(1099);
            registry.rebind("WatchTowerDiagnosis", this);
            log.info("DiagnosisServer: RMI registry started on port 1099 — " +
                     "bound as 'WatchTowerDiagnosis'");
        } catch (RemoteException e) {
            log.warn("DiagnosisServer: could not start RMI registry — {}. " +
                     "RmiController will be unavailable.", e.getMessage());
        }
    }

    @PreDestroy
    public void stopRmiServer() {
        try {
            if (registry != null) {
                registry.unbind("WatchTowerDiagnosis");
                UnicastRemoteObject.unexportObject(this, true);
                log.info("DiagnosisServer: RMI registry stopped.");
            }
        } catch (Exception e) {
            log.warn("DiagnosisServer: error during RMI shutdown — {}", e.getMessage());
        }
    }

    // ── Remote method implementations ──────────────────────────────────────

    @Override
    public double getTotalLoad() throws RemoteException {
        return analysisService.getTotalLoad();
    }

    @Override
    public long getUnresolvedAlertCount() throws RemoteException {
        return alertRepository.countByIsResolvedFalse();
    }

    @Override
    public List<Map<String, String>> getLastDiagnosisReport() throws RemoteException {
        List<Map<String, String>> report = new ArrayList<>();
        alertRepository.findByIsResolvedFalseOrderBySeverityDesc()
                .stream().limit(5)
                .forEach(alert -> report.add(Map.of(
                        "device",   alert.getDevice().getDeviceName(),
                        "type",     alert.getAlertType(),
                        "severity", alert.getSeverity().name(),
                        "message",  alert.getMessage()
                )));
        return report;
    }

    @Override
    public boolean isSimulatorRunning() throws RemoteException {
        return controlService.isRunning();
    }
}
