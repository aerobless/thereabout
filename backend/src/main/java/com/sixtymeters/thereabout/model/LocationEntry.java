package com.sixtymeters.thereabout.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class LocationEntry {
    private LocalDateTime timestamp;
    private double latitude;
    private double longitude;
    private double gpsAccuracy;
}
