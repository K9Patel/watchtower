package com.watchtower.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "health_score_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthScoreLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "score", nullable = false)
    private Short score;

    @Column(name = "bandwidth_score", nullable = false)
    private Short bandwidthScore;

    @Column(name = "latency_score", nullable = false)
    private Short latencyScore;

    @Column(name = "alert_score", nullable = false)
    private Short alertScore;

    @Column(name = "uptime_score", nullable = false)
    private Short uptimeScore;

    @Builder.Default
    @Column(name = "trend", nullable = false, length = 12)
    private String trend = "STABLE";

    @Column(name = "active_device_count", nullable = false)
    private Short activeDeviceCount;

    @Column(name = "active_alert_count", nullable = false)
    private Short activeAlertCount;

    @Builder.Default
    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt = LocalDateTime.now();
}
