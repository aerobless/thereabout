package com.sixtymeters.thereabout.model;

import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
public class LocationEntry {
    private LocalDateTime timestamp;
    private double latitude;
    private double longitude;
    private double gpsAccuracy;
}
