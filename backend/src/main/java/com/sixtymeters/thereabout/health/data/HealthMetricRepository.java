package com.sixtymeters.thereabout.health.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HealthMetricRepository extends JpaRepository<HealthMetricEntity, Long> {

    List<HealthMetricEntity> findByMetricNameAndMetricDate(String metricName, LocalDate metricDate);

    @Modifying
    @Query("DELETE FROM HealthMetricEntity h WHERE h.metricName = :metricName AND h.metricDate = :metricDate")
    void deleteByMetricNameAndMetricDate(@Param("metricName") String metricName, @Param("metricDate") LocalDate metricDate);

    List<HealthMetricEntity> findByMetricNameAndMetricDateBetween(String metricName, LocalDate fromDate, LocalDate toDate);

    List<HealthMetricEntity> findByMetricDateBetween(LocalDate fromDate, LocalDate toDate);
}
