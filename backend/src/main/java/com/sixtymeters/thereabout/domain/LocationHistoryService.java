package com.sixtymeters.thereabout.domain;

import com.sixtymeters.thereabout.domain.importer.GoogleLocationHistoryImporter;
import com.sixtymeters.thereabout.model.LocationEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationHistoryService {

    private final GoogleLocationHistoryImporter locationHistoryImporter;
    private List<LocationEntry> locationHistory;

    public List<LocationEntry> getFullLocationHistory() {
        final var locationHistory = locationHistoryImporter.importLocationHistory();
        log.info("Location history imported: " + locationHistory.size() + " entries");

        if(this.locationHistory == null) {
            this.locationHistory = locationHistory.stream()
                    .map(entry -> new LocationEntry(
                            entry.timestamp().toLocalDateTime(),
                            entry.latitudeE7() / 1E7,
                            entry.longitudeE7() / 1E7,
                            entry.accuracy()))
                    .toList();
        }

        return this.locationHistory;
    }

    public List<LocationEntry> getLocationHistory(LocalDate from, LocalDate to) {
        return getFullLocationHistory().stream()
                .filter(entry -> entry.getTimestamp().toLocalDate().isAfter(from.minusDays(1)))
                .filter(entry -> entry.getTimestamp().toLocalDate().isBefore(to.plusDays(1)))
                .toList();
    }
}
