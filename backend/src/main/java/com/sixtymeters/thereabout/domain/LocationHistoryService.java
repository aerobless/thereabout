package com.sixtymeters.thereabout.domain;

import com.google.common.collect.Lists;
import com.sixtymeters.thereabout.domain.importer.GoogleLocationHistoryImporter;
import com.sixtymeters.thereabout.model.LocationHistoryEntry;
import com.sixtymeters.thereabout.model.LocationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import uk.recurse.geocoding.reverse.Country;
import uk.recurse.geocoding.reverse.ReverseGeocoder;

import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationHistoryService {
    private final GoogleLocationHistoryImporter locationHistoryImporter;
    private final LocationHistoryRepository locationHistoryRepository;

    private final ReverseGeocoder reverseGeocoder = new ReverseGeocoder();

    private final static int CHUNK_SIZE = 10000;

    public List<LocationHistoryEntry> getLocationHistory(LocalDate from, LocalDate to) {
        return locationHistoryRepository.findAllByTimestampBetween(from.atStartOfDay(), to.atStartOfDay().plusDays(1));
    }

    @Async
    public void importGoogleLocationHistory(File file) {
        final var locationHistory = locationHistoryImporter.importLocationHistory(file);
        computeAdditionalFields(locationHistory);

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

    public LocationHistoryEntry createLocationHistoryEntry(LocationHistoryEntry locationHistoryEntry) {
        computeAdditionalFields(locationHistoryEntry);
        final var createdLocationHistory = locationHistoryRepository.save(locationHistoryEntry);
        log.info("Created location history entry with id %d.".formatted(createdLocationHistory.getId()));
        return createdLocationHistory;
    }

    private void computeAdditionalFields(List<LocationHistoryEntry> entries) {
        log.info("Computing additional fields for %d location history entries.".formatted(entries.size()));
        entries.forEach(this::computeAdditionalFields);
        log.info("Finished computing additional fields for %d location history entries.".formatted(entries.size()));
    }

    private void computeAdditionalFields(LocationHistoryEntry entry) {
        entry.setEstimatedIsoCountryCode(estimateCountryForCoordinates(entry));
    }

    private String estimateCountryForCoordinates(LocationHistoryEntry entry) {
        return reverseGeocoder.getCountry(entry.getLatitude(), entry.getLongitude())
                .map(Country::iso)
                .orElse(null);
    }

    public void deleteLocationHistoryEntry(long locationHistoryEntryId) {
        locationHistoryRepository.deleteById(locationHistoryEntryId);
        log.info("Deleted location history entry with id %d.".formatted(locationHistoryEntryId));
    }
}
