package com.sixtymeters.thereabout.health.data;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "health_metric_blood_pressure")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HealthMetricBloodPressureEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "health_metric_id", nullable = false)
    private HealthMetricEntity healthMetric;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal systolic;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal diastolic;
}
