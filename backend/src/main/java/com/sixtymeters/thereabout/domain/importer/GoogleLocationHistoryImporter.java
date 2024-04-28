package com.sixtymeters.thereabout.domain.importer;

import com.fatboyindustrial.gsonjavatime.Converters;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.io.IOException;
import java.util.List;

@Slf4j
@Service
public class GoogleLocationHistoryImporter {

    private static final String LOCATION_HISTORY_FILE = "/Users/thwi/git/thereabout/private-test-data/Records.json";

    public List<GoogleLocationEntry> importLocationHistory() {
        final Gson gson = Converters.registerZonedDateTime(new GsonBuilder()).create();
        try (FileReader reader = new FileReader(LOCATION_HISTORY_FILE)) {
            return gson.fromJson(reader, GoogleLocationHistory.class).locations();
        } catch (IOException e) {
            log.warn("Failed to read or parse the JSON file: " + e.getMessage());
            return null;
        }
    }
}
