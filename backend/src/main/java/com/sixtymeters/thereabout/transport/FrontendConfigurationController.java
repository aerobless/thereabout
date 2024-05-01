package com.sixtymeters.thereabout.transport;

import com.sixtymeters.thereabout.generated.api.FrontendApi;
import com.sixtymeters.thereabout.generated.model.GenFrontendConfigurationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class FrontendConfigurationController implements FrontendApi {

    @Value("${thereabout.apiKeys.googleMaps}")
    private String googleMapsApiKey;

    @Override
    public ResponseEntity<GenFrontendConfigurationResponse> getFrontendConfiguration() {
        log.info("Serving the frontend configuration.");
        return ResponseEntity.ok(GenFrontendConfigurationResponse.builder()
                .googleMapsApiKey(googleMapsApiKey)
                .build());
    }
}
