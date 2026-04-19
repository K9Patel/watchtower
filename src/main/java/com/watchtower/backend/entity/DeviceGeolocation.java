package com.watchtower.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "device_geolocation")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceGeolocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false, unique = true)
    @JsonIgnore
    private Device device;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "country_code", length = 5)
    private String countryCode;

    @Column(name = "country_name", length = 100)
    private String countryName;

    @Column(name = "region_name", length = 100)
    private String regionName;

    @Column(name = "city_name", length = 100)
    private String cityName;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "isp_name", length = 255)
    private String ispName;

    @Column(name = "timezone", length = 60)
    private String timezone;

    @Builder.Default
    @Column(name = "is_private", nullable = false)
    private Boolean isPrivate = false;

    @Builder.Default
    @Column(name = "source", nullable = false, length = 20)
    private String source = "IP_API";

    @Builder.Default
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated = LocalDateTime.now();
}
