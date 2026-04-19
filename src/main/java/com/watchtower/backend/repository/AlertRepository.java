package com.watchtower.backend.repository;

import com.watchtower.backend.entity.Alert;
import com.watchtower.backend.entity.Device;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    // Dashboard alert feed: unresolved alerts, newest first
    List<Alert> findByIsResolvedFalseOrderByCreatedAtDesc();

    List<Alert> findByIsResolvedFalseAndCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);

    // Compatibility query used by recommendation and RMI layers.
    List<Alert> findByIsResolvedFalseOrderBySeverityDesc();

    // EmailAlertService: check existing unresolved alerts per device
    List<Alert> findByDeviceAndIsResolvedFalse(Device device);

    // AlertController: full paginated alert history
    Page<Alert> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Alert> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since, Pageable pageable);

    // DiagnosisEngine: filter alerts by severity level
    List<Alert> findBySeverity(Alert.Severity severity);

    // Dashboard navbar badge: count of open alerts
    long countByIsResolvedFalse();

    long countByIsResolvedFalseAndCreatedAtAfter(LocalDateTime since);

    // Timeline replay: ordered alerts from a rolling window
    List<Alert> findByCreatedAtAfterOrderByCreatedAtAsc(java.time.LocalDateTime since);
}