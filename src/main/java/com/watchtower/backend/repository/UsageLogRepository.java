package com.watchtower.backend.repository;

import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.UsageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UsageLogRepository extends JpaRepository<UsageLog, Long> {

    // DiagnosisEngine: all logs from last 60s for rule evaluation
    List<UsageLog> findByTimestampAfter(LocalDateTime timestamp);

    // NetworkMonitorService: logs for ONE device in last 60s (Mbps calc)
    List<UsageLog> findByDeviceAndTimestampAfter(Device device, LocalDateTime timestamp);

    // Z-Score anomaly detection: last 100 readings per device
    List<UsageLog> findTop100ByDeviceOrderByTimestampDesc(Device device);

    // StatsController: paginated history feed
    Page<UsageLog> findAllByOrderByTimestampDesc(Pageable pageable);

    // HistoryAnalysisService: daily usage totals — PostgreSQL DATE() function
    @Query("SELECT CAST(u.timestamp AS date), SUM(u.bytesUsed) " +
           "FROM UsageLog u WHERE u.timestamp >= :since " +
           "GROUP BY CAST(u.timestamp AS date) " +
           "ORDER BY CAST(u.timestamp AS date)")
    List<Object[]> findDailyTotals(@Param("since") LocalDateTime since);

    // HistoryAnalysisService: peak hours heatmap — avg load per hour
    @Query("SELECT EXTRACT(HOUR FROM u.timestamp), AVG(u.bandwidthPercentage) " +
           "FROM UsageLog u WHERE u.timestamp >= :since " +
           "GROUP BY EXTRACT(HOUR FROM u.timestamp) " +
           "ORDER BY EXTRACT(HOUR FROM u.timestamp)")
    List<Object[]> findPeakHours(@Param("since") LocalDateTime since);

    // TrendAnalysisService: last 30 readings for linear regression
    @Query("SELECT u FROM UsageLog u ORDER BY u.timestamp DESC")
    List<UsageLog> findTop30ForTrend(Pageable pageable);
}