package com.sixtymeters.thereabout.health.service;

import com.sixtymeters.thereabout.generated.model.GenHeartRateHistory;
import com.sixtymeters.thereabout.generated.model.GenHrvHistory;
import com.sixtymeters.thereabout.health.data.HealthMetricHeartRateRepository;
import com.sixtymeters.thereabout.health.data.HealthMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HeartHistoryService {
    private final HealthMetricRepository metrics;
    private final HealthMetricHeartRateRepository heartRates;

    public GenHeartRateHistory heartRate(com.sixtymeters.thereabout.access.UserId user, LocalDate date, int days) {
        LocalDate start = date.minusDays(days - 1L);
        return HeartRateHistoryCalculator.calculate(heartRates.findHistory(user.value(), start, date),
                metrics.findByUserIdAndMetricNameAndMetricDateBetween(user.value(), "resting_heart_rate", start, date), date, days);
    }
    public GenHrvHistory hrv(com.sixtymeters.thereabout.access.UserId user, LocalDate date, int days) {
        // Thirty preceding days warm up even the earliest plotted personal range.
        LocalDate start = date.minusDays(days - 1L + 30);
        return HrvHistoryCalculator.calculate(metrics.findByUserIdAndMetricNameAndMetricDateBetween(user.value(), "heart_rate_variability", start, date), date, days);
    }
}
