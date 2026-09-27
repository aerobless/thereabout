package com.sixtymeters.thereabout.access;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Configuration
@ConditionalOnProperty(name = "thereabout.users.enabled", havingValue = "true")
public class UserAccessConfiguration {
    @Bean
    JwtDecoder userAccessTokenDecoder(
            @Value("${thereabout.users.access-issuer:${thereabout.finances.access-issuer:}}") String issuer,
            @Value("${thereabout.users.access-audience:${thereabout.finances.access-audience:}}") String audience) {
        return CloudflareAccessTokens.decoder(issuer, audience);
    }
}
