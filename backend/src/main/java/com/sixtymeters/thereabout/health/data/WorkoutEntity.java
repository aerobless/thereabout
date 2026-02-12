package com.sixtymeters.thereabout.health.data;

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
    private String id;

    private String name;

    @Column(nullable = false)
    private LocalDateTime start;

    @Column(nullable = false)
    private LocalDateTime end;

    private Integer duration;

    @Column(length = 50)
    private String location;

    @Column(precision = 10, scale = 2)
    private BigDecimal activeEnergyBurnedQty;

    @Column(length = 50)
    private String activeEnergyBurnedUnits;

    @Column(precision = 10, scale = 2)
    private BigDecimal intensityQty;

    @Column(length = 50)
    private String intensityUnits;

    @Column(precision = 10, scale = 2)
    private BigDecimal distanceQty;

    @Column(length = 50)
    private String distanceUnits;

    @Column(precision = 10, scale = 2)
    private BigDecimal temperatureQty;

    @Column(length = 50)
    private String temperatureUnits;

    @Column(precision = 5, scale = 2)
    private BigDecimal humidityQty;

    @Column(length = 50)
    private String humidityUnits;

    @Column(precision = 10, scale = 2)
    private BigDecimal avgSpeedQty;

    @Column(length = 50)
    private String avgSpeedUnits;

    @Column(precision = 10, scale = 2)
    private BigDecimal maxSpeedQty;

    @Column(length = 50)
    private String maxSpeedUnits;

    @Column(precision = 10, scale = 2)
    private BigDecimal elevationUpQty;

    @Column(length = 50)
    private String elevationUpUnits;

    @Column(precision = 10, scale = 2)
    private BigDecimal elevationDownQty;

    @Column(length = 50)
    private String elevationDownUnits;

    @Column(precision = 10, scale = 2)
    private BigDecimal lapLengthQty;

    @Column(length = 50)
    private String lapLengthUnits;

    @Column(length = 50)
    private String strokeStyle;

    private Integer swolfScore;

    @Column(length = 50)
    private String salinity;

    @Column(updatable = false)
    private java.time.Instant createdAt;

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
