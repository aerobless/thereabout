package com.sixtymeters.thereabout.domain;

import com.sixtymeters.thereabout.model.LocationHistoryRepository;
import com.sixtymeters.thereabout.model.LocationListEntity;
import com.sixtymeters.thereabout.model.LocationListRepository;
import com.sixtymeters.thereabout.support.ThereaboutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationListService {

    private final LocationListRepository locationListRepository;
    private final LocationHistoryRepository locationHistoryRepository;
    private final LocationHistoryService locationHistoryService;

    @Transactional
    public LocationListEntity addLocationHistoryList(LocationListEntity locationHistoryList) {
        return locationListRepository.save(locationHistoryList);
    }

    @Transactional
    public void deleteLocationHistoryList(long locationHistoryListId) {
        locationListRepository.deleteById(locationHistoryListId);
    }

    public List<LocationListEntity> getAllLocationHistoryLists() {
        return locationListRepository.findAll();
    }

    public LocationListEntity getLocationHistoryList(long locationHistoryListId) {
        return locationListRepository.findById(locationHistoryListId)
                .orElseThrow(() -> new ThereaboutException(HttpStatus.NOT_FOUND, "LocationHistoryList with id %s not found".formatted(locationHistoryListId)));
    }

    @Transactional
    public void addLocationToList(long listId, long locationHistoryEntryId) {
        LocationListEntity locationList = locationListRepository.findById(listId)
                .orElseThrow(() -> new ThereaboutException(HttpStatus.NOT_FOUND, "LocationHistoryList with id %s not found".formatted(listId)));

        locationHistoryRepository.findById(locationHistoryEntryId)
                .ifPresent(locationHistoryEntry -> locationList.getLocationHistoryEntries().add(locationHistoryEntry));

        locationListRepository.save(locationList);
    }

    @Transactional
    public void removeLocationFromList(long listId, long locationHistoryEntryId) {
        LocationListEntity locationList = locationListRepository.findById(listId)
                .orElseThrow(() -> new ThereaboutException(HttpStatus.NOT_FOUND, "LocationHistoryList with id %s not found".formatted(listId)));

        locationList.getLocationHistoryEntries().removeIf(entry -> entry.getId().equals(locationHistoryEntryId));

        locationListRepository.save(locationList);
    }
}