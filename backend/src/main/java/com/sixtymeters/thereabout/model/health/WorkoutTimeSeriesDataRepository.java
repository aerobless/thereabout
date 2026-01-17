package com.sixtymeters.thereabout.model.health;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkoutTimeSeriesDataRepository extends JpaRepository<WorkoutTimeSeriesDataEntity, Long> {

    @Modifying
    @Query("DELETE FROM WorkoutTimeSeriesDataEntity w WHERE w.workout.id = :workoutId")
    void deleteByWorkoutId(@Param("workoutId") String workoutId);
}
