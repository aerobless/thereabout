package com.sixtymeters.thereabout.transport;

import com.sixtymeters.thereabout.domain.LocationHistoryService;
import com.sixtymeters.thereabout.generated.api.LocationApi;
import com.sixtymeters.thereabout.generated.model.GenAddGeoJsonLocation200Response;
import com.sixtymeters.thereabout.generated.model.GenAddGeoJsonLocationRequest;
import com.sixtymeters.thereabout.generated.model.GenLocationHistoryEntry;
import com.sixtymeters.thereabout.generated.model.GenSparseLocationHistoryEntry;
import com.sixtymeters.thereabout.model.LocationHistorySource;
import com.sixtymeters.thereabout.transport.mapper.LocationHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
public class LocationHistoryController implements LocationApi {

    private final LocationHistoryService locationHistoryService;
    private static final LocationHistoryMapper LOCATION_HISTORY_MAPPER = LocationHistoryMapper.INSTANCE;

    @Transactional
    @Override
    public ResponseEntity<GenAddGeoJsonLocation200Response> addGeoJsonLocation(String authorization, GenAddGeoJsonLocationRequest genAddGeoJsonLocationRequest) {
        log.info("Received GeoJson location data via HTTP Endpoint /backend/api/v1/location/add-geojson-location" + authorization);
        genAddGeoJsonLocationRequest.getLocations().stream()
                .map(LOCATION_HISTORY_MAPPER::map)
                .forEach(locationHistoryService::createLocationHistoryEntry);

        return ResponseEntity.ok(GenAddGeoJsonLocation200Response.builder().result("ok").build());
    }

    @Override
    public ResponseEntity<GenLocationHistoryEntry> addLocation(GenLocationHistoryEntry genLocationHistoryEntry) {
        final var locationHistoryEntryCreationRequest = LOCATION_HISTORY_MAPPER.map(genLocationHistoryEntry);
        locationHistoryEntryCreationRequest.setSource(LocationHistorySource.THEREABOUT_API);

        final var createdLocationHistoryEntry = locationHistoryService
                .createLocationHistoryEntry(locationHistoryEntryCreationRequest);

        return ResponseEntity.ok(LOCATION_HISTORY_MAPPER.map(createdLocationHistoryEntry));
    }

    @Override
    public ResponseEntity<Void> deleteLocation(BigDecimal id) {
        locationHistoryService.deleteLocationHistoryEntry(id.longValue());
        return ResponseEntity.noContent().build();
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
