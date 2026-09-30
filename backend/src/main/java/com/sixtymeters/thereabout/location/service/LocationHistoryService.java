package com.sixtymeters.thereabout.location.service;

import com.google.common.collect.Lists;
import com.sixtymeters.thereabout.client.service.ImportProgressService;
import com.sixtymeters.thereabout.location.service.importer.GoogleLocationHistoryImporter;
import com.sixtymeters.thereabout.location.data.LocationHistoryEntity;
import com.sixtymeters.thereabout.location.data.LocationHistoryRepository;
import com.sixtymeters.thereabout.location.data.LocationHistorySource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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


    private final static int CHUNK_SIZE = 10000;

    private final int MANUAL_ACCURACY = 0;

    public List<LocationHistoryEntity> getLocationHistory(com.sixtymeters.thereabout.access.UserId user, LocalDate from, LocalDate to) {
        return locationHistoryRepository.findAllByUserIdAndTimestampBetween(user.value(), from.atStartOfDay(), to.atStartOfDay().plusDays(1));
    }

    public List<LocationHistoryEntity> getSparseLocationHistory(com.sixtymeters.thereabout.access.UserId user, LocalDate from, LocalDate to) {
        final var allTimestamps = locationHistoryRepository.findAllByUserIdAndTimestampBetweenSparseSample(user.value(), from.atStartOfDay(), to.atStartOfDay().plusDays(1), 0.1);
        return allTimestamps;
    }

    public void importGoogleLocationHistory(com.sixtymeters.thereabout.access.UserId user, File file) {
        final var locationHistory = locationHistoryImporter.importLocationHistory(file);
        locationHistory.forEach(entry -> entry.setUserId(user.value()));

        AtomicLong importedCount = new AtomicLong();
        Lists.partition(locationHistory, CHUNK_SIZE).forEach(chunk -> {
            importedCount.addAndGet(chunk.size());
            locationHistoryRepository.saveAll(chunk);
            importProgressService.setProgress(calculatePercentage(locationHistory.size(), importedCount.get()));
            log.info("Imported %d%% of Google Location History.".formatted(importProgressService.getProgress()));
        });

        locationHistoryRepository.flush();
        log.info("Finished importing %d entries of Google Location History.".formatted(locationHistory.size()));
    }

    private int calculatePercentage(long total, long current) {
        int percentage = (int) ((current / (float) total) * 100);
        return Math.max(percentage, 1);
    }

    public LocationHistoryEntity createLocationHistoryEntry(com.sixtymeters.thereabout.access.UserId user, LocationHistoryEntity locationHistoryEntity) {
        locationHistoryEntity.setUserId(user.value());
        final var createdLocationHistory = locationHistoryRepository.save(locationHistoryEntity);
        log.info("Created location history entry with id %d.".formatted(createdLocationHistory.getId()));
        return createdLocationHistory;
    }

    @Transactional
    public void deleteLocationHistoryEntries(com.sixtymeters.thereabout.access.UserId user, List<Long> locationHistoryEntryIds) {
        var ids = locationHistoryEntryIds.stream().distinct().toList();
        var entries = locationHistoryRepository.findByIdInAndUserId(ids, user.value());
        if (entries.size() != ids.size()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Location not found");
        locationHistoryRepository.deleteAll(entries);
        log.info("Deleted %d location history entries.".formatted(locationHistoryEntryIds.size()));
    }

    @Transactional
    public LocationHistoryEntity updateLocationHistoryEntry(com.sixtymeters.thereabout.access.UserId user, long entryId, LocationHistoryEntity updateEntry) {
        final var existingEntry = locationHistoryRepository.findByIdAndUserId(entryId, user.value()).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Location not found"));

        existingEntry.setTimestamp(updateEntry.getTimestamp());
        existingEntry.setAltitude(updateEntry.getAltitude());
        existingEntry.setLatitude(updateEntry.getLatitude());
        existingEntry.setLongitude(updateEntry.getLongitude());
        existingEntry.setHorizontalAccuracy(MANUAL_ACCURACY);
        existingEntry.setVerticalAccuracy(MANUAL_ACCURACY);
        existingEntry.setSource(LocationHistorySource.THEREABOUT_API_UPDATE);
        existingEntry.setNote(updateEntry.getNote());
        return locationHistoryRepository.save(existingEntry);
    }
}
