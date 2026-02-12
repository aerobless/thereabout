package com.sixtymeters.thereabout.health.data;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "health_metric_heart_rate")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HealthMetricHeartRateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "health_metric_id", nullable = false)
    private HealthMetricEntity healthMetric;

    @Column(name = "min_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal minValue;

    @Column(name = "avg_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal avgValue;

    @Column(name = "max_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal maxValue;
}
