package com.sixtymeters.thereabout.finance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.*;

/**
 * Finance may use its own Access application. Its audience is then required for finance and MCP, so a
 * token for the general application cannot be replayed against the ledger.
 */
@Configuration
@ConditionalOnProperty(name = "thereabout.finances.access-mode", havingValue = "cloudflare")
public class FinanceCloudflareAccessConfiguration {
  @Bean
  JwtDecoder financeAccessTokenDecoder(
      @Value("${thereabout.finances.access-issuer}") String issuer,
      @Value("${thereabout.finances.access-audience}") String audience) {
    return com.sixtymeters.thereabout.access.CloudflareAccessTokens.decoder(issuer, audience);
  }
}
