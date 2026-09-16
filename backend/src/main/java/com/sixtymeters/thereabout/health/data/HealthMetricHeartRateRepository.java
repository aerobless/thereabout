package com.sixtymeters.thereabout.health.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HealthMetricHeartRateRepository extends JpaRepository<HealthMetricHeartRateEntity, Long> {
    @Query("SELECT h FROM HealthMetricHeartRateEntity h JOIN FETCH h.healthMetric m "
            + "WHERE m.metricName = 'heart_rate' AND m.metricDate BETWEEN :fromDate AND :toDate")
    List<HealthMetricHeartRateEntity> findHistory(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}
