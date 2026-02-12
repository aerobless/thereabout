package com.sixtymeters.thereabout.health.data;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Table(name = "health_metric_toothbrushing")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HealthMetricToothbrushingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "health_metric_id", nullable = false)
    private HealthMetricEntity healthMetric;

    @Column(length = 50)
    private String value;
}
