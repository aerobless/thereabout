package com.sixtymeters.thereabout.client.service;

import com.sixtymeters.thereabout.access.UserId;
import com.sixtymeters.thereabout.client.data.*;
import com.sixtymeters.thereabout.generated.model.GenPreferences;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

@Service @RequiredArgsConstructor
public class PreferencesService {
    private final UserPreferencesRepository repository;
    @Transactional(readOnly=true)
    public GenPreferences getPreferences(UserId user) {
        var value = repository.findById(user.value());
        return GenPreferences.builder()
                .weightGoalKg(value.map(UserPreferencesEntity::getWeightGoalKg).orElse(new BigDecimal("75.0")))
                .weightGoalStartedOn(value.map(UserPreferencesEntity::getWeightGoalStartedOn).orElse(today())).build();
    }
    @Transactional
    public GenPreferences updateGoal(UserId user, BigDecimal goal) {
        if (goal == null || !Double.isFinite(goal.doubleValue()) || goal.signum() <= 0 || goal.stripTrailingZeros().scale() > 1 || goal.toPlainString().length() > 1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Goal must be positive with at most one decimal place");
        var existing = repository.findById(user.value());
        if (existing.isPresent() && goal.compareTo(existing.get().getWeightGoalKg()) == 0) return getPreferences(user);
        var value = existing.orElseGet(UserPreferencesEntity::new);
        value.setUserId(user.value()); value.setWeightGoalKg(goal);
        value.setWeightGoalStartedOn(today()); repository.save(value);
        return getPreferences(user);
    }
    private static LocalDate today() { return LocalDate.now(ZoneId.of("Europe/Zurich")); }
}
