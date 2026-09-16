package com.sixtymeters.thereabout.health.transport;

import com.sixtymeters.thereabout.generated.api.HeartApi;
import com.sixtymeters.thereabout.generated.model.GenHeartRateHistory;
import com.sixtymeters.thereabout.generated.model.GenHrvHistory;
import com.sixtymeters.thereabout.health.service.HeartHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class HeartHistoryController implements HeartApi {
    private final HeartHistoryService history;

    @Override
    public ResponseEntity<GenHeartRateHistory> getHeartRateHistory(LocalDate date, Integer days) {
        validate(date, days);
        return ResponseEntity.ok(history.heartRate(date, days));
    }
    @Override
    public ResponseEntity<GenHrvHistory> getHrvHistory(LocalDate date, Integer days) {
        validate(date, days);
        return ResponseEntity.ok(history.hrv(date, days));
    }
    private static void validate(LocalDate date, Integer days) {
        if (date == null || days == null || (days != 7 && days != 30))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a date and a 7 or 30 day range");
    }
}
