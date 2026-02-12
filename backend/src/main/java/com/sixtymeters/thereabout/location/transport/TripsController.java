package com.sixtymeters.thereabout.location.transport;

import com.sixtymeters.thereabout.location.service.LocationHistoryService;
import com.sixtymeters.thereabout.location.service.TripsService;
import com.sixtymeters.thereabout.generated.api.TripApi;
import com.sixtymeters.thereabout.generated.model.GenTrip;
import com.sixtymeters.thereabout.generated.model.GenTripVisitedCountriesInner;
import com.sixtymeters.thereabout.location.data.LocationHistoryEntity;
import com.sixtymeters.thereabout.location.data.TripEntity;
import com.sixtymeters.thereabout.config.ThereaboutException;
import com.sixtymeters.thereabout.location.transport.mapper.TripMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.recurse.geocoding.reverse.ReverseGeocoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
public class TripsController implements TripApi {

    private static final TripMapper TRIP_MAPPER = TripMapper.INSTANCE;
    private final TripsService tripsService;
    private final LocationHistoryService locationHistoryService;
    private final ReverseGeocoder reverseGeocoder = new ReverseGeocoder();

    @Override
    public ResponseEntity<GenTrip> addTrip(GenTrip genTrip) {
        TripEntity tripToCreate = TRIP_MAPPER.mapToTripEntity(genTrip);
        TripEntity savedTrip = tripsService.addTrip(tripToCreate);
        GenTrip response = TRIP_MAPPER.mapToGenTrip(savedTrip);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<Void> deleteTrip(BigDecimal tripId) {
        tripsService.deleteTrip(tripId.longValue());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<GenTrip>> getTrips() {
        return ResponseEntity.ok(tripsService.getAllTrips().stream()
                .map(TRIP_MAPPER::mapToGenTrip)
                .map(this::addCountryInformation)
                .collect(Collectors.toList()));
    }

    private GenTrip addCountryInformation(GenTrip trip) {
        final var locationHistoryOfTrip = locationHistoryService.getLocationHistory(trip.getStart(), trip.getEnd());
        locationHistoryOfTrip.stream()
                .map(LocationHistoryEntity::getEstimatedIsoCountryCode)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(country -> trip.addVisitedCountriesItem(GenTripVisitedCountriesInner.builder()
                        .countryIsoCode(country)
                        .countryName(reverseGeocoder.countries()
                                .filter(c -> c.iso().equals(country))
                                .findFirst()
                                .orElseThrow(() -> new ThereaboutException(HttpStatusCode.valueOf(500), "Country %s not found".formatted(country)))
                                .name())
                        .build()));
        return trip;
    }

    @Override
    public ResponseEntity<GenTrip> updateTrip(BigDecimal tripId, GenTrip genTrip) {
        TripEntity tripToBeUpdated = TRIP_MAPPER.mapToTripEntity(genTrip);
        TripEntity updatedTrip = tripsService.updateTrip(tripId.longValue(), tripToBeUpdated);
        GenTrip response = TRIP_MAPPER.mapToGenTrip(updatedTrip);
        return ResponseEntity.ok(response);
    }
}
