package com.watchtower.backend.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DpiDeviceDto {

    private Long deviceId;
    private String deviceName;
    private String ipAddress;

    private String currentService;
    private String currentCategory;
    private String sniHostname;
    private String destinationIp;
    private Integer destinationPort;
    private Short confidence;

    private LocalDateTime lastUpdated;
}
