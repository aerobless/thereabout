package com.sixtymeters.thereabout.domain.importer;

import com.fatboyindustrial.gsonjavatime.Converters;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.io.IOException;
import java.util.List;

@Slf4j
@Service
public class GoogleLocationHistoryImporter {

    @Value("${thereabout.import.import-folder}")
    private String LOCATION_HISTORY_PATH;

    public List<GoogleLocationEntry> importLocationHistory() {
        final var importFile = "%s/Records.json".formatted(LOCATION_HISTORY_PATH);
        log.info("Loading location history from file: " + importFile);

        final Gson gson = Converters.registerZonedDateTime(new GsonBuilder()).create();
        try (FileReader reader = new FileReader(importFile)) {
            List<GoogleLocationEntry> locationHistory = gson.fromJson(reader, GoogleLocationHistory.class).locations();
            log.info("Successfully loaded %d location entries".formatted(locationHistory.size()));
            return locationHistory;
        } catch (IOException e) {
            log.warn("Failed to read or parse the JSON file: " + e.getMessage());
            return null;
        }
    }
}
