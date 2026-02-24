package com.sixtymeters.thereabout.health.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface HealthMetricSleepRepository extends JpaRepository<HealthMetricSleepEntity, Long> {

    List<HealthMetricSleepEntity> findByHealthMetricIn(Collection<HealthMetricEntity> healthMetrics);
}
