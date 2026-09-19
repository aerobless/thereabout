package com.sixtymeters.thereabout.choices.transport;

import com.sixtymeters.thereabout.choices.service.ChoicesService;
import com.sixtymeters.thereabout.generated.api.ChoicesApi;
import com.sixtymeters.thereabout.generated.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class ChoicesController implements ChoicesApi {
    private final ChoicesService service;
    @Override
    public ResponseEntity<GenChoicesHistory> getChoicesHistory(LocalDate date, Integer days) {
        return ResponseEntity.ok(service.history(date, days));
    }
    @Override
    public ResponseEntity<GenChoicesDay> adjustChoices(LocalDate date, GenChoicesAdjustment request) {
        try {
            Integer delta = request == null || request.getDelta() == null ? null : request.getDelta().intValueExact();
            return ResponseEntity.ok(service.adjust(date, delta));
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delta must be exactly -1 or 1");
        }
    }
}
