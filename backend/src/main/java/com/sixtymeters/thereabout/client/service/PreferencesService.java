package com.sixtymeters.thereabout.client.service;

import com.sixtymeters.thereabout.client.data.ConfigurationEntity;
import com.sixtymeters.thereabout.client.data.ConfigurationKey;
import com.sixtymeters.thereabout.client.data.ConfigurationRepository;
import com.sixtymeters.thereabout.generated.model.GenPreferences;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PreferencesService {
    private final ConfigurationRepository repository;

    @Transactional(readOnly = true)
    public GenPreferences getPreferences() {
        var values = repository.findAllById(List.of(ConfigurationKey.WEIGHT_GOAL_KG, ConfigurationKey.WEIGHT_GOAL_STARTED_ON))
                .stream().collect(Collectors.toMap(ConfigurationEntity::getConfigKey, ConfigurationEntity::getConfigValue));
        return GenPreferences.builder()
                .weightGoalKg(new BigDecimal(values.get(ConfigurationKey.WEIGHT_GOAL_KG)))
                .weightGoalStartedOn(LocalDate.parse(values.get(ConfigurationKey.WEIGHT_GOAL_STARTED_ON)))
                .build();
    }

    @Transactional
    public GenPreferences updateGoal(BigDecimal goal) {
        if (goal == null || goal.signum() <= 0 || !Double.isFinite(goal.doubleValue()) || goal.stripTrailingZeros().scale() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Goal must be positive with at most one decimal place");
        }
        var current = getPreferences();
        if (goal.compareTo(current.getWeightGoalKg()) == 0) return current;
        LocalDate today = LocalDate.now();
        repository.saveAll(List.of(
                ConfigurationEntity.builder().configKey(ConfigurationKey.WEIGHT_GOAL_KG).configValue(goal.stripTrailingZeros().toString()).build(),
                ConfigurationEntity.builder().configKey(ConfigurationKey.WEIGHT_GOAL_STARTED_ON).configValue(today.toString()).build()));
        return GenPreferences.builder().weightGoalKg(goal).weightGoalStartedOn(today).build();
    }
}
