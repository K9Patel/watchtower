package com.watchtower.backend.repository;

import com.watchtower.backend.entity.DeviceBaseline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceBaselineRepository extends JpaRepository<DeviceBaseline, Long> {

    /** AnomalyDetectionRule: lookup baseline for current device + hour */
    Optional<DeviceBaseline> findByDeviceIdAndHourOfDay(Long deviceId, short hourOfDay);

    /** Device detail page: all 24 baseline slots for overlay chart */
    List<DeviceBaseline> findAllByDeviceIdOrderByHourOfDay(Long deviceId);

    /** BaselineService: check how many hour slots have been populated */
    long countByDeviceId(Long deviceId);

    /** Cleanup: delete all baselines for a device (e.g., on device removal) */
    void deleteByDeviceId(Long deviceId);
}
