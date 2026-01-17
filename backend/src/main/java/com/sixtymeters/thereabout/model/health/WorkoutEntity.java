package com.sixtymeters.thereabout.model.health;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "workout")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkoutEntity {

    @Id
    @Column(name = "id", length = 255)
    private String id;

    @Column(name = "name")
    private String name;

    @Column(name = "start", nullable = false)
    private LocalDateTime start;

    @Column(name = "end", nullable = false)
    private LocalDateTime end;

    @Column(name = "duration")
    private Integer duration;

    @Column(name = "location", length = 50)
    private String location;

    @Column(name = "active_energy_burned_qty", precision = 10, scale = 2)
    private BigDecimal activeEnergyBurnedQty;

    @Column(name = "active_energy_burned_units", length = 50)
    private String activeEnergyBurnedUnits;

    @Column(name = "intensity_qty", precision = 10, scale = 2)
    private BigDecimal intensityQty;

    @Column(name = "intensity_units", length = 50)
    private String intensityUnits;

    @Column(name = "distance_qty", precision = 10, scale = 2)
    private BigDecimal distanceQty;

    @Column(name = "distance_units", length = 50)
    private String distanceUnits;

    @Column(name = "temperature_qty", precision = 10, scale = 2)
    private BigDecimal temperatureQty;

    @Column(name = "temperature_units", length = 50)
    private String temperatureUnits;

    @Column(name = "humidity_qty", precision = 5, scale = 2)
    private BigDecimal humidityQty;

    @Column(name = "humidity_units", length = 50)
    private String humidityUnits;

    @Column(name = "avg_speed_qty", precision = 10, scale = 2)
    private BigDecimal avgSpeedQty;

    @Column(name = "avg_speed_units", length = 50)
    private String avgSpeedUnits;

    @Column(name = "max_speed_qty", precision = 10, scale = 2)
    private BigDecimal maxSpeedQty;

    @Column(name = "max_speed_units", length = 50)
    private String maxSpeedUnits;

    @Column(name = "elevation_up_qty", precision = 10, scale = 2)
    private BigDecimal elevationUpQty;

    @Column(name = "elevation_up_units", length = 50)
    private String elevationUpUnits;

    @Column(name = "elevation_down_qty", precision = 10, scale = 2)
    private BigDecimal elevationDownQty;

    @Column(name = "elevation_down_units", length = 50)
    private String elevationDownUnits;

    @Column(name = "lap_length_qty", precision = 10, scale = 2)
    private BigDecimal lapLengthQty;

    @Column(name = "lap_length_units", length = 50)
    private String lapLengthUnits;

    @Column(name = "stroke_style", length = 50)
    private String strokeStyle;

    @Column(name = "swolf_score")
    private Integer swolfScore;

    @Column(name = "salinity", length = 50)
    private String salinity;

    @Column(name = "created_at", updatable = false)
    private java.time.Instant createdAt;

    @Column(name = "updated_at")
    private java.time.Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = java.time.Instant.now();
        updatedAt = java.time.Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = java.time.Instant.now();
    }
}
