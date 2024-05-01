package com.sixtymeters.thereabout.domain;

import com.google.common.collect.Lists;
import com.sixtymeters.thereabout.domain.importer.GoogleLocationHistoryImporter;
import com.sixtymeters.thereabout.model.LocationHistoryEntry;
import com.sixtymeters.thereabout.model.LocationHistoryRepository;
import com.sixtymeters.thereabout.model.LocationHistorySource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationHistoryService {
    private final GoogleLocationHistoryImporter locationHistoryImporter;
    private final LocationHistoryRepository locationHistoryRepository;
    private final static int CHUNK_SIZE = 10000;

    public List<LocationHistoryEntry> getLocationHistory(LocalDate from, LocalDate to) {
        if(locationHistoryRepository.countBySource(LocationHistorySource.GOOGLE_IMPORT) == 0) {
            importLocationHistoryFromGoogleFile();
        }

        return locationHistoryRepository.findAllByTimestampBetween(from.atStartOfDay(), to.atStartOfDay());
    }

    private void importLocationHistoryFromGoogleFile() {
        final var locationHistory = locationHistoryImporter.importLocationHistory();

        AtomicLong importedCount = new AtomicLong();
        Lists.partition(locationHistory, CHUNK_SIZE).forEach(chunk -> {
            importedCount.addAndGet(chunk.size());
            locationHistoryRepository.saveAll(chunk);
            log.info("Imported %d%% of Google Location History.".formatted(calculatePercentage(locationHistory.size(), importedCount.get())));
        });

        locationHistoryRepository.flush();
        log.info("Finished importing %d entries of Google Location History.".formatted(locationHistory.size()));
    }

    private int calculatePercentage(long total, long current) {
        return (int) ((current / (float) total) * 100);
    }
}
