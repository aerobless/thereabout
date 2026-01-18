package com.sixtymeters.thereabout.domain.health;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyMetricValue {
    private LocalDate date;
    private BigDecimal qty;
    private String units;
    private LocalDateTime timestamp;
    private String source;
}
