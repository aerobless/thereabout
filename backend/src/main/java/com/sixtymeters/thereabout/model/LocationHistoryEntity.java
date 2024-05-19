package com.sixtymeters.thereabout.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "location_history_entry")
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class LocationHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private LocalDateTime timestamp;

    @Column
    private double latitude;

    @Column
    private double longitude;

    @Column
    private int horizontalAccuracy;

    @Column
    private int verticalAccuracy;

    @Column
    private int altitude;

    // in degrees
    @Column
    private int heading;

    // in m/s
    @Column
    private int velocity;

    @Column
    @Enumerated(EnumType.STRING)
    private LocationHistorySource source;

    @Column
    private String estimatedIsoCountryCode;
}
