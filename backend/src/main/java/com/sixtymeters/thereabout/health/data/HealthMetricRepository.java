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

    List<HealthMetricEntity> findByUserIdAndMetricNameAndMetricDate(long userId, String metricName, LocalDate metricDate);

    @Modifying
    @Query("DELETE FROM HealthMetricEntity h WHERE h.userId = :userId AND h.metricName = :metricName AND h.metricDate = :metricDate")
    void deleteByUserIdAndMetricNameAndMetricDate(@Param("userId") long userId, @Param("metricName") String metricName, @Param("metricDate") LocalDate metricDate);

    List<HealthMetricEntity> findByUserIdAndMetricNameAndMetricDateBetween(long userId, String metricName, LocalDate fromDate, LocalDate toDate);

    List<HealthMetricEntity> findByUserIdAndMetricNameInAndMetricDateLessThanEqual(long userId, List<String> metricNames, LocalDate date);

    List<HealthMetricEntity> findByUserIdAndMetricDateBetween(long userId, LocalDate fromDate, LocalDate toDate);
}
