package com.sixtymeters.thereabout.health.data;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Table(name = "health_metric_sexual_activity")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HealthMetricSexualActivityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "health_metric_id", nullable = false)
    private HealthMetricEntity healthMetric;

    private Integer unspecified;

    private Integer protectionUsed;

    private Integer protectionNotUsed;
}
