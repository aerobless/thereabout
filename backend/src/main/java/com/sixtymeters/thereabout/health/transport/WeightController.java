package com.sixtymeters.thereabout.health.transport;

import com.sixtymeters.thereabout.client.service.PreferencesService;
import com.sixtymeters.thereabout.generated.api.WeightApi;
import com.sixtymeters.thereabout.generated.model.GenWeightProgress;
import com.sixtymeters.thereabout.health.data.HealthMetricRepository;
import com.sixtymeters.thereabout.health.service.WeightProgressCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class WeightController implements WeightApi {
    private final HealthMetricRepository repository;
    private final PreferencesService preferences;

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<GenWeightProgress> getWeightProgress(LocalDate date, Integer days) {
        if (date == null || days == null || (days != 7 && days != 30)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a date and a 7 or 30 day range");
        var readings = repository.findByMetricNameInAndMetricDateLessThanEqual(WeightProgressCalculator.METRICS, date);
        return ResponseEntity.ok(WeightProgressCalculator.calculate(readings, preferences.getPreferences(), date, days));
    }
}
