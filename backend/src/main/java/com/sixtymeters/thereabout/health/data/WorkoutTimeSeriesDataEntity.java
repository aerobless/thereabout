package com.sixtymeters.thereabout.health.data;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "workout_time_series_data")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkoutTimeSeriesDataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_id", nullable = false)
    private WorkoutEntity workout;

    @Column(name = "data_type", nullable = false, length = 50)
    private String dataType;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "qty", precision = 10, scale = 2)
    private BigDecimal qty;

    @Column(name = "units", length = 50)
    private String units;

    @Column(name = "source", length = 255)
    private String source;

    @Column(name = "min_value", precision = 10, scale = 2)
    private BigDecimal minValue;

    @Column(name = "avg_value", precision = 10, scale = 2)
    private BigDecimal avgValue;

    @Column(name = "max_value", precision = 10, scale = 2)
    private BigDecimal maxValue;
}
