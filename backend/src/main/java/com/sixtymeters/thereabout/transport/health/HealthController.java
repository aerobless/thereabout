package com.sixtymeters.thereabout.transport.health;

import com.sixtymeters.thereabout.generated.api.HealthApi;
import com.sixtymeters.thereabout.generated.model.GenSubmitHealthDataRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class HealthController implements HealthApi {

    @Override
    public ResponseEntity<Void> submitHealthData(GenSubmitHealthDataRequest genSubmitHealthDataRequest) {
        log.info("submitHealthData: {}", genSubmitHealthDataRequest);
        return null;
    }
}
