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

    @Column(name = "total_sleep", precision = 10, scale = 2)
    private BigDecimal totalSleep;

    @Column(name = "asleep", precision = 10, scale = 2)
    private BigDecimal asleep;

    @Column(name = "awake", precision = 10, scale = 2)
    private BigDecimal awake;

    @Column(name = "core", precision = 10, scale = 2)
    private BigDecimal core;

    @Column(name = "deep", precision = 10, scale = 2)
    private BigDecimal deep;

    @Column(name = "rem", precision = 10, scale = 2)
    private BigDecimal rem;

    @Column(name = "sleep_start")
    private LocalDateTime sleepStart;

    @Column(name = "sleep_end")
    private LocalDateTime sleepEnd;

    @Column(name = "in_bed", precision = 10, scale = 2)
    private BigDecimal inBed;

    @Column(name = "in_bed_start")
    private LocalDateTime inBedStart;

    @Column(name = "in_bed_end")
    private LocalDateTime inBedEnd;
}
