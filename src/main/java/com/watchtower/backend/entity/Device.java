package com.watchtower.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "device")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "mac_address", length = 17)
    private String macAddress;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Builder.Default
    @Column(name = "status", length = 10)
    private String status = "ONLINE";

    @Builder.Default
    @Column(name = "registered_at")
    private LocalDateTime registeredAt = LocalDateTime.now();

    // Tracks the last time this device was seen in a live network scan.
    // If this is null or very old → device has left the network.
    @Builder.Default
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt = LocalDateTime.now();
    
}