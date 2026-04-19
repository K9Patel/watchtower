package com.watchtower.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Per-device hourly bandwidth baseline — used by the anomaly detection engine
 * to compare live bandwidth against statistical "normal" for each time slot.
 *
 * 24 rows per device (one per hour-of-day).
 * Built by BaselineService after a 1-day learning period.
 *
 * Database table: device_baseline (V6 migration)
 */
@Entity
@Table(name = "device_baseline")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceBaseline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    /** Hour of day: 0–23 (stored as SMALLINT in PostgreSQL) */
    @Column(name = "hour_of_day", nullable = false)
    private Short hourOfDay;

    /** Rolling average bandwidth percentage for this hour slot */
    @Column(name = "avg_bandwidth", nullable = false)
    @Builder.Default
    private Double avgBandwidth = 0.0;

    /** Standard deviation of bandwidth for this hour slot */
    @Column(name = "stddev_bandwidth", nullable = false)
    @Builder.Default
    private Double stddevBandwidth = 0.0;

    /** Number of samples used to compute the baseline */
    @Column(name = "sample_count", nullable = false)
    @Builder.Default
    private Integer sampleCount = 0;

    @Column(name = "last_updated", nullable = false)
    @Builder.Default
    private LocalDateTime lastUpdated = LocalDateTime.now();
}
