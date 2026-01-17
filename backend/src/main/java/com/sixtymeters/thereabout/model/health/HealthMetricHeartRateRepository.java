package com.sixtymeters.thereabout.model.health;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HealthMetricHeartRateRepository extends JpaRepository<HealthMetricHeartRateEntity, Long> {
}
