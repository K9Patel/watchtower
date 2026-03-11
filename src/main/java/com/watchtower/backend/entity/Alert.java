package com.watchtower.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "alert")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType;           // ANOMALY | CONGESTION | SPIKE | STREAMING_OVERLOAD

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private Severity severity;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;             // plain-English from DiagnosisEngine

    @Builder.Default
    @Column(name = "is_resolved", nullable = false)
    private Boolean isResolved = false;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }
}