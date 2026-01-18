package com.sixtymeters.thereabout.domain.health;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutSummary {
    private String id;
    private String name;
    private LocalDateTime start;
    private LocalDateTime end;
    private Integer duration;
    private String location;
    private BigDecimal activeEnergyBurnedQty;
    private String activeEnergyBurnedUnits;
    private BigDecimal distanceQty;
    private String distanceUnits;
}
