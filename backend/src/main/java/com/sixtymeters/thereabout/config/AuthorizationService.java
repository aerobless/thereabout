package com.sixtymeters.thereabout.config;

import com.sixtymeters.thereabout.client.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final ConfigurationService configurationService;

    public void isAuthorised(String requestAuthHeader) {
        final var requestAuthKey = requestAuthHeader.replace("Bearer ", "");
        if(!requestAuthKey.equals(configurationService.getThereaboutApiKey())) {
            throw new ThereaboutException(HttpStatusCode.valueOf(401), "Unauthorized access.");
        }
    }
}
