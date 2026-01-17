package com.sixtymeters.thereabout.model.health;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "health_metric_heart_rate_notification")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HealthMetricHeartRateNotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "health_metric_id", nullable = false)
    private HealthMetricEntity healthMetric;

    @Column(name = "start", nullable = false)
    private LocalDateTime start;

    @Column(name = "end", nullable = false)
    private LocalDateTime end;

    @Column(name = "threshold", nullable = false, precision = 10, scale = 2)
    private BigDecimal threshold;
}
