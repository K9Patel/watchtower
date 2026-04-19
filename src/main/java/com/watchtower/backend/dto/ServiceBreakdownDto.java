package com.watchtower.backend.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceBreakdownDto {

    private String serviceName;
    private String trafficCategory;
    private Long events;
    private Long bytesCaptured;
    private Double sharePercent;
    private Double averageConfidence;
}
