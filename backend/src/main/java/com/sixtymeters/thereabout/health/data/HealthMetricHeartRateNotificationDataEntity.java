package com.sixtymeters.thereabout.health.data;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "health_metric_heart_rate_notification_data")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HealthMetricHeartRateNotificationDataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "health_metric_hrn_id", nullable = false)
    private HealthMetricHeartRateNotificationEntity healthMetricHeartRateNotification;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal hr;

    @Column(length = 50)
    private String units;

    private LocalDateTime timestampStart;

    private LocalDateTime timestampEnd;

    @Column(precision = 10, scale = 2)
    private BigDecimal intervalDuration;

    @Column(length = 50)
    private String intervalUnits;
}
