package com.sixtymeters.thereabout.model.health;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Table(name = "health_metric_insulin")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HealthMetricInsulinEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "health_metric_id", nullable = false)
    private HealthMetricEntity healthMetric;

    @Column(name = "reason", length = 50)
    private String reason;
}
