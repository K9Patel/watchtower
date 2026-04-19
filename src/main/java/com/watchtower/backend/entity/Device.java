package com.watchtower.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Unified device entity — no DeviceType enum.
 *
 * Database columns (cumulative after V1-V5 Flyway migrations):
 *   V1: id, device_name, ip_address, mac_address, is_active, status, registered_at
 *   V2: (mac_address index)
 *   V3: last_seen_at
 *   V4: (device_type column dropped)
 *   V5: is_auto_discovered, vendor_name, os_type
 */
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

    // ── V1 core fields ────────────────────────────────────────────────────────

    /**
     * Device name or hostname — resolved from reverse DNS lookup.
     * If DNS has no record for the IP, this is null → frontend shows the IP address.
     * Never contains generated names like "Device-A1B2" — only real hostnames or null.
     */
    @Column(name = "device_name", nullable = true, length = 100)
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

    // ── V3: live heartbeat ────────────────────────────────────────────────────
    // Updated every 60 s by NetworkDiscoveryService when device is in ARP table.
    // If NULL or older than 5 min → device has left the network.

    @Builder.Default
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt = LocalDateTime.now();

    // ── V5: auto-discovery fields ─────────────────────────────────────────────

    /**
     * TRUE  → device was found by ARP scan; auto-removed after 5 min absence.
     * FALSE → device was manually added; kept until explicitly deleted.
     */
    @Builder.Default
    @Column(name = "is_auto_discovered", nullable = false)
    private Boolean isAutoDiscovered = false;

    /**
     * MAC vendor resolved from OUI prefix — e.g. "Apple, Inc.", "Samsung Electronics".
     * Populated by MacVendorService (Feature 3). NULL until populated.
     */
    @Column(name = "vendor_name", length = 100)
    private String vendorName;

    /**
     * OS fingerprint — e.g. "Windows 11", "Android 14", "iOS 17".
     * Populated by OS fingerprinting service (Feature 6). NULL until populated.
     */
    @Column(name = "os_type", length = 50)
    private String osType;

    // ── V6: baseline learning fields ──────────────────────────────────────────

    /**
     * TRUE once the baseline engine has accumulated enough samples (1 day of data)
     * for meaningful anomaly scoring. Until then, AnomalyDetectionRule is DISABLED.
     */
    @Builder.Default
    @Column(name = "baseline_ready", nullable = false)
    private Boolean baselineReady = false;

    /**
     * Timestamp when the baseline learning period started for this device.
     * Set by BaselineService on first encounter. NULL before first baseline run.
     */
    @Column(name = "baseline_since")
    private LocalDateTime baselineSince;
}