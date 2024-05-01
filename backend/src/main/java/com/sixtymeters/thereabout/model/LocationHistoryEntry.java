package com.sixtymeters.thereabout.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class LocationHistoryEntry {

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
    private double gpsAccuracy;

    @Column
    private LocationHistorySource source;
}
