package com.sixtymeters.thereabout.health.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WorkoutRepository extends JpaRepository<WorkoutEntity, String> {
    List<WorkoutEntity> findByStartBetween(LocalDateTime from, LocalDateTime to);
}
