package com.sixtymeters.thereabout.transport;

import com.sixtymeters.thereabout.domain.LocationHistoryService;
import com.sixtymeters.thereabout.generated.api.LocationApi;
import com.sixtymeters.thereabout.generated.model.GenLocationHistoryEntry;
import com.sixtymeters.thereabout.generated.model.GenSparseLocationHistoryEntry;
import com.sixtymeters.thereabout.model.LocationHistorySource;
import com.sixtymeters.thereabout.transport.mapper.LocationHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class LocationHistoryController implements LocationApi {

    private final LocationHistoryService locationHistoryService;
    private static final LocationHistoryMapper LOCATION_HISTORY_MAPPER = LocationHistoryMapper.INSTANCE;

    @Override
    public ResponseEntity<GenLocationHistoryEntry> addLocation(GenLocationHistoryEntry genLocationHistoryEntry) {
        final var locationHistoryEntryCreationRequest = LOCATION_HISTORY_MAPPER.map(genLocationHistoryEntry);
        locationHistoryEntryCreationRequest.setSource(LocationHistorySource.THEREABOUT_API);

        final var createdLocationHistoryEntry = locationHistoryService
                .createLocationHistoryEntry(locationHistoryEntryCreationRequest);

        return ResponseEntity.ok(LOCATION_HISTORY_MAPPER.map(createdLocationHistoryEntry));
    }

    @Override
    public ResponseEntity<List<GenLocationHistoryEntry>> getLocations(Optional<LocalDate> from, Optional<LocalDate> to) {
        final var filteredLocationHistory =  locationHistoryService.getLocationHistory(
                from.orElse(LocalDate.now().minusYears(100L)),
                to.orElse(LocalDate.now().plusYears(100L))
        );

        final var locationHistory = filteredLocationHistory.stream()
                .map(LOCATION_HISTORY_MAPPER::map)
                .toList();

        return ResponseEntity.ok(locationHistory);
    }

    @Override
    public ResponseEntity<List<GenSparseLocationHistoryEntry>> getSparseLocations(Optional<LocalDate> from, Optional<LocalDate> to) {
        final var filteredLocationHistory =  locationHistoryService.getLocationHistory(
                from.orElse(LocalDate.now().minusYears(100L)),
                to.orElse(LocalDate.now().plusYears(100L))
        );

        final var locationHistory = filteredLocationHistory.stream()
                .map(LOCATION_HISTORY_MAPPER::mapToSparseEntry)
                .toList();

        return ResponseEntity.ok(locationHistory);
    }

}
