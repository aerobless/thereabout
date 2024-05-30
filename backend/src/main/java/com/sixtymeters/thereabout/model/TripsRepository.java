package com.sixtymeters.thereabout.model;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TripsRepository extends JpaRepository<TripEntity, Long> {
}
