package com.watchtower.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usage_log")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // AJT Unit 8 — ORM: @ManyToOne = many logs belong to one device
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "bytes_used", nullable = false)
    private Double bytesUsed;           // megabytes

    @Column(name = "bandwidth_percentage", nullable = false)
    private Double bandwidthPercentage; // 0.0 – 100.0

    @Column(name = "traffic_type", nullable = false, length = 20)
    private String trafficType;         // STREAMING | BROWSING | GAMING | DOWNLOAD | VOIP

    @Builder.Default
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();
}