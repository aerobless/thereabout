package com.sixtymeters.thereabout.location.service;

import com.google.common.collect.Lists;
import com.sixtymeters.thereabout.client.service.ImportProgressService;
import com.sixtymeters.thereabout.location.service.importer.GoogleLocationHistoryImporter;
import com.sixtymeters.thereabout.location.data.LocationHistoryEntity;
import com.sixtymeters.thereabout.location.data.LocationHistoryRepository;
import com.sixtymeters.thereabout.location.data.LocationHistorySource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final ImportProgressService importProgressService;

    private final ReverseGeocoder reverseGeocoder = new ReverseGeocoder();

    private final static int CHUNK_SIZE = 10000;

    private final int MANUAL_ACCURACY = 0;

    public List<LocationHistoryEntity> getLocationHistory(LocalDate from, LocalDate to) {
        return locationHistoryRepository.findAllByTimestampBetween(from.atStartOfDay(), to.atStartOfDay().plusDays(1));
    }

    public List<LocationHistoryEntity> getSparseLocationHistory(LocalDate from, LocalDate to) {
        final var allTimestamps = locationHistoryRepository.findAllByTimestampBetweenSparseSample(from.atStartOfDay(), to.atStartOfDay().plusDays(1), 0.1);
        return allTimestamps;
    }

    @Async
    public void importGoogleLocationHistory(File file) {
        importProgressService.setProgress(1);
        final var locationHistory = locationHistoryImporter.importLocationHistory(file);
        computeAdditionalFields(locationHistory);

        AtomicLong importedCount = new AtomicLong();
        Lists.partition(locationHistory, CHUNK_SIZE).forEach(chunk -> {
            importedCount.addAndGet(chunk.size());
            locationHistoryRepository.saveAll(chunk);
            importProgressService.setProgress(calculatePercentage(locationHistory.size(), importedCount.get()));
            log.info("Imported %d%% of Google Location History.".formatted(importProgressService.getProgress()));
        });

        locationHistoryRepository.flush();
        importProgressService.reset();
        log.info("Finished importing %d entries of Google Location History.".formatted(locationHistory.size()));
    }

    private int calculatePercentage(long total, long current) {
        int percentage = (int) ((current / (float) total) * 100);
        return Math.max(percentage, 1);
    }

    public LocationHistoryEntity createLocationHistoryEntry(LocationHistoryEntity locationHistoryEntity) {
        computeAdditionalFields(locationHistoryEntity);
        final var createdLocationHistory = locationHistoryRepository.save(locationHistoryEntity);
        log.info("Created location history entry with id %d.".formatted(createdLocationHistory.getId()));
        return createdLocationHistory;
    }

    private void computeAdditionalFields(List<LocationHistoryEntity> entries) {
        log.info("Computing additional fields for %d location history entries.".formatted(entries.size()));
        entries.forEach(this::computeAdditionalFields);
        log.info("Finished computing additional fields for %d location history entries.".formatted(entries.size()));
    }

    private void computeAdditionalFields(LocationHistoryEntity entry) {
        entry.setEstimatedIsoCountryCode(estimateCountryForCoordinates(entry));
    }

    private String estimateCountryForCoordinates(LocationHistoryEntity entry) {
        return reverseGeocoder.getCountry(entry.getLatitude(), entry.getLongitude())
                .map(Country::iso)
                .orElse(null);
    }

    public void deleteLocationHistoryEntries(List<Long> locationHistoryEntryIds) {
        locationHistoryRepository.deleteAllById(locationHistoryEntryIds);
        log.info("Deleted %d location history entries.".formatted(locationHistoryEntryIds.size()));
    }

    @Transactional
    public LocationHistoryEntity updateLocationHistoryEntry(long entryId, LocationHistoryEntity updateEntry) {
        final var existingEntry = locationHistoryRepository.findById(entryId).orElseThrow();

        existingEntry.setTimestamp(updateEntry.getTimestamp());
        existingEntry.setAltitude(updateEntry.getAltitude());
        existingEntry.setLatitude(updateEntry.getLatitude());
        existingEntry.setLongitude(updateEntry.getLongitude());
        existingEntry.setHorizontalAccuracy(MANUAL_ACCURACY);
        existingEntry.setVerticalAccuracy(MANUAL_ACCURACY);
        existingEntry.setSource(LocationHistorySource.THEREABOUT_API_UPDATE);
        existingEntry.setEstimatedIsoCountryCode(estimateCountryForCoordinates(updateEntry));
        existingEntry.setNote(updateEntry.getNote());
        return locationHistoryRepository.save(existingEntry);
    }
}
