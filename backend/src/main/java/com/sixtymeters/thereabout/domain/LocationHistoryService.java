package com.sixtymeters.thereabout.domain;

import com.sixtymeters.thereabout.domain.importer.GoogleLocationHistoryImporter;
import com.sixtymeters.thereabout.model.LocationEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationHistoryService {

    private final GoogleLocationHistoryImporter locationHistoryImporter;

    public List<LocationEntry> getLocationHistory() {
        final var locationHistory = locationHistoryImporter.importLocationHistory();
        log.info("Location history imported: " + locationHistory.size() + " entries");
        return locationHistory.stream()
                .map(entry -> new LocationEntry(
                        entry.timestamp().toLocalDateTime(),
                        entry.latitudeE7() / 1E7,
                        entry.longitudeE7() / 1E7,
                        entry.accuracy()))
                .toList();
    }
}
