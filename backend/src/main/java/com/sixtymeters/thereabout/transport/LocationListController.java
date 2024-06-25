package com.sixtymeters.thereabout.transport;

import com.sixtymeters.thereabout.domain.LocationListService;
import com.sixtymeters.thereabout.generated.api.LocationListApi;
import com.sixtymeters.thereabout.generated.model.GenLocationHistoryList;
import com.sixtymeters.thereabout.model.LocationListEntity;
import com.sixtymeters.thereabout.transport.mapper.LocationListMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class LocationListController implements LocationListApi {

    private final LocationListService locationListService;
    private static final LocationListMapper LOCATION_LIST_MAPPER = LocationListMapper.INSTANCE;

    @Override
    public ResponseEntity<GenLocationHistoryList> createLocationHistoryList(GenLocationHistoryList genLocationHistoryList) {
        LocationListEntity locationHistoryList = LOCATION_LIST_MAPPER.map(genLocationHistoryList);
        LocationListEntity createdList = locationListService.addLocationHistoryList(locationHistoryList);
        GenLocationHistoryList response = LOCATION_LIST_MAPPER.map(createdList);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteLocationHistoryList(BigDecimal id) {
        locationListService.deleteLocationHistoryList(id.longValue());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<GenLocationHistoryList> getLocationHistoryList(BigDecimal id) {
        LocationListEntity locationHistoryList = locationListService.getLocationHistoryList(id.longValue());
        GenLocationHistoryList response = LOCATION_LIST_MAPPER.map(locationHistoryList);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<List<GenLocationHistoryList>> getLocationHistoryLists() {
        List<LocationListEntity> locationHistoryLists = locationListService.getAllLocationHistoryLists();
        List<GenLocationHistoryList> response = locationHistoryLists.stream()
                .map(LOCATION_LIST_MAPPER::map)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<GenLocationHistoryList> updateLocationHistoryList(BigDecimal id, GenLocationHistoryList genLocationHistoryList) {
        LocationListEntity locationHistoryList = LOCATION_LIST_MAPPER.map(genLocationHistoryList);
        LocationListEntity updatedList = locationListService.updateLocationHistoryList(id.longValue(), locationHistoryList);
        GenLocationHistoryList response = LOCATION_LIST_MAPPER.map(updatedList);
        return ResponseEntity.ok(response);
    }
}

