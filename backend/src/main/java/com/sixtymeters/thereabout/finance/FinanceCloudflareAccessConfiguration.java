package com.sixtymeters.thereabout.finance;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.util.Assert;

/**
 * Validates Cloudflare signatures as well as the application audience, issuer and token lifetime.
 */
@Configuration
@ConditionalOnProperty(name = "thereabout.finances.access-mode", havingValue = "cloudflare")
public class FinanceCloudflareAccessConfiguration {
  @Bean
  JwtDecoder financeAccessTokenDecoder(
      @Value("${thereabout.finances.access-issuer}") String issuer,
      @Value("${thereabout.finances.access-audience}") String audience,
      @Value("${thereabout.finances.public-origin}") String origin) {
    URI issuerUri = URI.create(issuer);
    Assert.isTrue(
        "https".equals(issuerUri.getScheme()) && issuerUri.getHost() != null,
        "Finance Access issuer must be HTTPS");
    Assert.hasText(audience, "Finance Access audience is required");
    URI originUri = URI.create(origin);
    Assert.isTrue(
        "https".equals(originUri.getScheme())
            && originUri.getHost() != null
            && originUri.getRawQuery() == null
            && originUri.getRawFragment() == null
            && originUri.getUserInfo() == null
            && originUri.getPath().isEmpty(),
        "Finance public origin must be an HTTPS origin without a path");
    return com.sixtymeters.thereabout.access.CloudflareAccessTokens.decoder(issuer, audience);
  }

  static OAuth2TokenValidator<Jwt> validators(String issuer, String audience) {
    return com.sixtymeters.thereabout.access.CloudflareAccessTokens.validators(issuer, audience);
  }
}
