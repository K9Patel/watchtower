package com.watchtower.backend.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DpiTrafficEntryDto {

    private Long id;
    private String serviceName;
    private String trafficCategory;
    private String sniHostname;
    private String destinationIp;
    private Integer port;
    private Integer packetsCount;
    private Long bytesCaptured;
    private Short confidence;
    private LocalDateTime classifiedAt;
}
