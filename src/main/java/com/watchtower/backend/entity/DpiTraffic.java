package com.watchtower.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "dpi_traffic")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DpiTraffic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Builder.Default
    @Column(name = "service_name", nullable = false, length = 30)
    private String serviceName = "UNKNOWN";

    @Builder.Default
    @Column(name = "traffic_category", nullable = false, length = 20)
    private String trafficCategory = "BROWSING";

    @Column(name = "sni_hostname", length = 255)
    private String sniHostname;

    @Column(name = "destination_ip", length = 45)
    private String destinationIp;

    @Column(name = "port")
    private Integer port;

    @Builder.Default
    @Column(name = "packets_count", nullable = false)
    private Integer packetsCount = 1;

    @Builder.Default
    @Column(name = "bytes_captured", nullable = false)
    private Long bytesCaptured = 0L;

    @Builder.Default
    @Column(name = "confidence", nullable = false)
    private Short confidence = 50;

    @Builder.Default
    @Column(name = "classified_at", nullable = false)
    private LocalDateTime classifiedAt = LocalDateTime.now();
}
