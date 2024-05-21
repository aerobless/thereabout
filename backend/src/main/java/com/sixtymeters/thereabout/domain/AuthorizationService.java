package com.sixtymeters.thereabout.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final ConfigurationService configurationService;

    public void isAuthorised(String requestAuth) {
        if(!requestAuth.equals(configurationService.getThereaboutApiKey())) {
            log.error("Unauthorized access. Key %s does not match the expected key %s.".formatted(requestAuth, configurationService.getThereaboutApiKey()));
            //throw new ThereaboutException(HttpStatusCode.valueOf(401), "Unauthorized access.");
        }
    }
}
