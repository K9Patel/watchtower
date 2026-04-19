package com.watchtower.backend.repository;

import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.DpiTraffic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DpiTrafficRepository extends JpaRepository<DpiTraffic, Long> {

    Optional<DpiTraffic> findTopByDeviceOrderByClassifiedAtDesc(Device device);

    List<DpiTraffic> findByDeviceAndClassifiedAtAfterOrderByClassifiedAtDesc(Device device, LocalDateTime since);

    List<DpiTraffic> findByClassifiedAtAfterOrderByClassifiedAtDesc(LocalDateTime since);

    @Query("SELECT d.serviceName, COUNT(d), COALESCE(SUM(d.bytesCaptured), 0), COALESCE(AVG(d.confidence), 0) " +
            "FROM DpiTraffic d WHERE d.classifiedAt >= :since GROUP BY d.serviceName ORDER BY COUNT(d) DESC")
    List<Object[]> summarizeByServiceSince(@Param("since") LocalDateTime since);

    @Query("SELECT d FROM DpiTraffic d WHERE d.classifiedAt >= :since " +
            "AND UPPER(d.trafficCategory) = 'STREAMING' ORDER BY d.classifiedAt DESC")
    List<DpiTraffic> findRecentStreamingSince(@Param("since") LocalDateTime since);
}
