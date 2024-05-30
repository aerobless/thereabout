package com.sixtymeters.thereabout.domain;

import com.sixtymeters.thereabout.model.TripEntity;
import com.sixtymeters.thereabout.model.TripsRepository;
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
public class TripsService {

    private final TripsRepository tripsRepository;

    public TripEntity addTrip(TripEntity tripEntity) {
        return tripsRepository.save(tripEntity);
    }

    public void deleteTrip(long tripId) {
        tripsRepository.deleteById(tripId);
    }

    public List<TripEntity> getAllTrips() {
        return tripsRepository.findAll();
    }

    public TripEntity updateTrip(long tripId, TripEntity tripEntity) {
        Optional<TripEntity> existingTrip = tripsRepository.findById(tripId);
        if (existingTrip.isPresent()) {
            tripEntity.setId(tripId);
            return tripsRepository.save(tripEntity);
        } else {
            throw new ThereaboutException(HttpStatus.NOT_FOUND, "Trip with id %s not found".formatted(tripId));
        }
    }
}
