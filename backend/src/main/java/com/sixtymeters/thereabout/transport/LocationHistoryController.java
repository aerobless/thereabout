package com.sixtymeters.thereabout.transport;

import com.sixtymeters.thereabout.domain.LocationHistoryService;
import com.sixtymeters.thereabout.generated.api.LocationApi;
import com.sixtymeters.thereabout.generated.model.GenLocationHistoryEntry;
import com.sixtymeters.thereabout.model.LocationEntry;
import com.sixtymeters.thereabout.transport.mapper.LocationHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class LocationHistoryController implements LocationApi {

    private final LocationHistoryService locationHistoryService;
    private static final LocationHistoryMapper LOCATION_HISTORY_MAPPER = LocationHistoryMapper.INSTANCE;

    @Override
    public ResponseEntity<List<GenLocationHistoryEntry>> getLocations() {
        final var locationHistory = locationHistoryService.getLocationHistory().stream()
                .map(LOCATION_HISTORY_MAPPER::map)
                .toList();

        return ResponseEntity.ok(locationHistory);
    }
}
