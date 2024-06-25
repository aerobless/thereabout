package com.sixtymeters.thereabout.domain;

import com.sixtymeters.thereabout.model.LocationListEntity;
import com.sixtymeters.thereabout.model.LocationListRepository;
import com.sixtymeters.thereabout.support.ThereaboutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationListService {

    private final LocationListRepository locationListRepository;

    public LocationListEntity addLocationHistoryList(LocationListEntity locationHistoryList) {
        return locationListRepository.save(locationHistoryList);
    }

    public void deleteLocationHistoryList(long locationHistoryListId) {
        locationListRepository.deleteById(locationHistoryListId);
    }

    public List<LocationListEntity> getAllLocationHistoryLists() {
        return locationListRepository.findAll();
    }

    public LocationListEntity updateLocationHistoryList(long locationHistoryListId, LocationListEntity locationHistoryList) {
        Optional<LocationListEntity> existingLocationHistoryList = locationListRepository.findById(locationHistoryListId);
        if (existingLocationHistoryList.isPresent()) {
            locationHistoryList.setId(locationHistoryListId);
            return locationListRepository.save(locationHistoryList);
        } else {
            throw new ThereaboutException(HttpStatus.NOT_FOUND, "LocationHistoryList with id %s not found".formatted(locationHistoryListId));
        }
    }

    public LocationListEntity getLocationHistoryList(long locationHistoryListId) {
        return locationListRepository.findById(locationHistoryListId)
                .orElseThrow(() -> new ThereaboutException(HttpStatus.NOT_FOUND, "LocationHistoryList with id %s not found".formatted(locationHistoryListId)));
    }
}