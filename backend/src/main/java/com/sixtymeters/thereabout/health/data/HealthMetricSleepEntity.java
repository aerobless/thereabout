package com.sixtymeters.thereabout.health.data;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "health_metric_sleep")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HealthMetricSleepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "health_metric_id")
    private HealthMetricEntity healthMetric;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalSleep;

    @Column(precision = 10, scale = 2)
    private BigDecimal asleep;

    @Column(precision = 10, scale = 2)
    private BigDecimal awake;

    @Column(precision = 10, scale = 2)
    private BigDecimal core;

    @Column(precision = 10, scale = 2)
    private BigDecimal deep;

    @Column(precision = 10, scale = 2)
    private BigDecimal rem;

    private LocalDateTime sleepStart;

    private LocalDateTime sleepEnd;

    @Column(precision = 10, scale = 2)
    private BigDecimal inBed;

    private LocalDateTime inBedStart;

    private LocalDateTime inBedEnd;
}
