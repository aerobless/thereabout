package com.sixtymeters.thereabout.client.transport;

import com.sixtymeters.thereabout.client.service.PreferencesService;
import com.sixtymeters.thereabout.generated.api.PreferencesApi;
import com.sixtymeters.thereabout.generated.model.GenPreferences;
import com.sixtymeters.thereabout.generated.model.GenUpdatePreferences;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PreferencesController implements PreferencesApi {
    private final PreferencesService service;

    @Override
    public ResponseEntity<GenPreferences> getPreferences() {
        return ResponseEntity.ok(service.getPreferences());
    }

    @Override
    public ResponseEntity<GenPreferences> updatePreferences(GenUpdatePreferences request) {
        return ResponseEntity.ok(service.updateGoal(request == null ? null : request.getWeightGoalKg()));
    }
}
