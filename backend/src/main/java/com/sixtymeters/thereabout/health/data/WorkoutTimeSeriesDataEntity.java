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

    @Column(nullable = false, length = 50)
    private String dataType;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(precision = 10, scale = 2)
    private BigDecimal qty;

    @Column(length = 50)
    private String units;

    private String source;

    @Column(precision = 10, scale = 2)
    private BigDecimal minValue;

    @Column(precision = 10, scale = 2)
    private BigDecimal avgValue;

    @Column(precision = 10, scale = 2)
    private BigDecimal maxValue;
}
