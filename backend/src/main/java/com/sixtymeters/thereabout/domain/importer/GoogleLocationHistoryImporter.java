package com.sixtymeters.thereabout.domain.importer;

import com.fatboyindustrial.gsonjavatime.Converters;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sixtymeters.thereabout.model.LocationHistoryEntry;
import com.sixtymeters.thereabout.model.LocationHistorySource;
import com.sixtymeters.thereabout.support.ThereaboutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.io.IOException;
import java.util.List;

@Slf4j
@Service
public class GoogleLocationHistoryImporter {

    @Value("${thereabout.import.import-folder}")
    private String LOCATION_HISTORY_PATH;

    public List<LocationHistoryEntry> importLocationHistory() {
        final var importFile = "%s/Records.json".formatted(LOCATION_HISTORY_PATH);
        log.info("Loading location history from file: " + importFile);

        final Gson gson = Converters.registerZonedDateTime(new GsonBuilder()).create();
        try (FileReader reader = new FileReader(importFile)) {
            List<GoogleLocationEntry> locationHistory = gson.fromJson(reader, GoogleLocationHistory.class).locations();
            log.info("Successfully loaded %d location entries".formatted(locationHistory.size()));
            return locationHistory.stream()
                    .map(this::mapToGenericLocationHistoryEntry)
                    .toList();
        } catch (IOException e) {
            throw new ThereaboutException(HttpStatusCode.valueOf(500),
                    "Failed to read or parse the Records.json file from '%s': %s".formatted(importFile, e.getMessage()));
        }
    }

    private LocationHistoryEntry mapToGenericLocationHistoryEntry(GoogleLocationEntry entry) {
        return LocationHistoryEntry.builder()
                .timestamp(entry.timestamp().toLocalDateTime())
                .latitude(entry.latitudeE7() / 1E7)
                .longitude(entry.longitudeE7() / 1E7)
                .horizontalAccuracy(entry.accuracy())
                .verticalAccuracy(entry.verticalAccuracy())
                .altitude(entry.altitude())
                .heading(entry.heading())
                .velocity(entry.velocity())
                .source(LocationHistorySource.GOOGLE_IMPORT)
                .build();
    }
}
