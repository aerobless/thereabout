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
                new LocationEntry(LocalDateTime.now(), 47.3769, 8.5417, 10),
                new LocationEntry(LocalDateTime.now().minusHours(1), 47.3769, 8.5417, 10),
                new LocationEntry(LocalDateTime.now().minusHours(2), 47.3769, 8.5417, 10)
        );
    }
}
