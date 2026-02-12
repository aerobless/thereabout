package com.sixtymeters.thereabout.location.data;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TripsRepository extends JpaRepository<TripEntity, Long> {
}
