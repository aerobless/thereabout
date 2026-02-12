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

    @Column(name = "hr", nullable = false, precision = 10, scale = 2)
    private BigDecimal hr;

    @Column(name = "units", length = 50)
    private String units;

    @Column(name = "timestamp_start")
    private LocalDateTime timestampStart;

    @Column(name = "timestamp_end")
    private LocalDateTime timestampEnd;

    @Column(name = "interval_duration", precision = 10, scale = 2)
    private BigDecimal intervalDuration;

    @Column(name = "interval_units", length = 50)
    private String intervalUnits;
}
