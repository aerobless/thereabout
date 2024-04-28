package com.sixtymeters.thereabout.domain;

import com.sixtymeters.thereabout.model.LocationEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class LocationHistoryService {

    public List<LocationEntry> getLocationHistory() {
        log.info("Returning mock location history");

        return List.of(
                new LocationEntry(LocalDateTime.now(), 37.782, -122.447, 10),
                new LocationEntry(LocalDateTime.now(), 37.782, -122.445, 10),
                new LocationEntry(LocalDateTime.now(), 37.782, -122.443, 10),
                new LocationEntry(LocalDateTime.now(), 37.782, -122.441, 10),
                new LocationEntry(LocalDateTime.now(), 37.782, -122.439, 10),
                new LocationEntry(LocalDateTime.now(), 37.782, -122.437, 10),
                new LocationEntry(LocalDateTime.now(), 37.782, -122.435, 10),
                new LocationEntry(LocalDateTime.now(), 37.785, -122.447, 10),
                new LocationEntry(LocalDateTime.now(), 37.785, -122.445, 10),
                new LocationEntry(LocalDateTime.now(), 37.785, -122.443, 10),
                new LocationEntry(LocalDateTime.now(), 37.785, -122.441, 10),
                new LocationEntry(LocalDateTime.now(), 37.785, -122.439, 10),
                new LocationEntry(LocalDateTime.now(), 37.785, -122.437, 10),
                new LocationEntry(LocalDateTime.now(), 37.785, -122.435, 10)
        );
    }
}
