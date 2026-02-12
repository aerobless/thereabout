package com.sixtymeters.thereabout.health.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HealthMetricHeartRateNotificationRepository extends JpaRepository<HealthMetricHeartRateNotificationEntity, Long> {
}
