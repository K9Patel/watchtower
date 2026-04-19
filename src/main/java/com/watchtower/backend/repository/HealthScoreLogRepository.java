package com.watchtower.backend.repository;

import com.watchtower.backend.entity.HealthScoreLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface HealthScoreLogRepository extends JpaRepository<HealthScoreLog, Long> {

    Optional<HealthScoreLog> findTopByOrderByRecordedAtDesc();

    List<HealthScoreLog> findByRecordedAtAfterOrderByRecordedAtAsc(LocalDateTime since);

    @Query("SELECT CAST(h.recordedAt AS date), AVG(h.score) " +
           "FROM HealthScoreLog h WHERE h.recordedAt >= :since " +
           "GROUP BY CAST(h.recordedAt AS date) " +
           "ORDER BY CAST(h.recordedAt AS date)")
    List<Object[]> findDailyAverageScores(@Param("since") LocalDateTime since);
}
