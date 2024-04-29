package com.sixtymeters.thereabout.transport;

import com.sixtymeters.thereabout.domain.LocationHistoryService;
import com.sixtymeters.thereabout.generated.api.LocationApi;
import com.sixtymeters.thereabout.generated.model.GenLocationHistoryEntry;
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

}
