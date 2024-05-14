package com.sixtymeters.thereabout.domain.importer;

import com.fatboyindustrial.gsonjavatime.Converters;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sixtymeters.thereabout.model.LocationHistoryEntry;
import com.sixtymeters.thereabout.model.LocationHistorySource;
import com.sixtymeters.thereabout.support.ThereaboutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.List;

@Slf4j
@Service
public class GoogleLocationHistoryImporter {

    public List<LocationHistoryEntry> importLocationHistory(final File file) {
        final Gson gson = Converters.registerZonedDateTime(new GsonBuilder()).create();
        try (Reader reader = new FileReader(file)) {
            List<GoogleLocationEntry> locationHistory = gson.fromJson(reader, GoogleLocationHistory.class).locations();
            if (locationHistory == null || locationHistory.isEmpty()) {
                throw new ThereaboutException(HttpStatusCode.valueOf(400),
                        "There is not location data in file '%s'".formatted(file.getName()));
            }
            log.info("Successfully loaded %d location entries".formatted(locationHistory.size()));
            return locationHistory.stream()
                    .map(this::mapToGenericLocationHistoryEntry)
                    .toList();
        } catch (IOException e) {
            throw new ThereaboutException(HttpStatusCode.valueOf(400),
                    "Failed to read or parse the file from '%s' due to %s".formatted(file.getName(), e.getMessage()));
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
