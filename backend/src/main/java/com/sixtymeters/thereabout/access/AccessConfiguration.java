package com.sixtymeters.thereabout.access;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Configuration
@EnableConfigurationProperties(AccessProperties.class)
public class AccessConfiguration {
    /** Verification of Access assertions; {@code decoder} is null without Cloudflare settings. */
    public record CloudflareAccess(JwtDecoder decoder) {}

    /** Incomplete or invalid settings fail startup. */
    @Bean
    CloudflareAccess cloudflareAccess(AccessProperties properties) {
        var cloudflare = properties.cloudflare();
        return new CloudflareAccess(cloudflare.configured()
                ? CloudflareAccessTokens.decoder(cloudflare.issuer(), cloudflare.audience()) : null);
    }
}
