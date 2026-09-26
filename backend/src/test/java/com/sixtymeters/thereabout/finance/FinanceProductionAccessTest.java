package com.sixtymeters.thereabout.finance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.util.ReflectionTestUtils;

class FinanceProductionAccessTest {
  private static final String ORIGIN = "https://finance.example.test";
  private static final String ISSUER = "https://team.cloudflareaccess.com";

  @SuppressWarnings("unchecked")
  private FinanceAccessFilter filter() {
    JwtDecoder decoder =
        token -> {
          if (!"valid-token".equals(token)) throw new BadJwtException("Invalid signature");
          return jwt("application", ISSUER, Instant.now().plusSeconds(300));
        };
    ObjectProvider<JwtDecoder> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(decoder);
    var keys = mock(com.sixtymeters.thereabout.finance.service.FinanceMcpKeyService.class);
    when(keys.matchesAuthorization("Bearer a-long-test-mcp-key-with-at-least-32-characters"))
        .thenReturn(true);
    var filter = new FinanceAccessFilter(provider, keys);
    ReflectionTestUtils.setField(filter, "enabled", true);
    ReflectionTestUtils.setField(filter, "accessMode", "cloudflare");
    ReflectionTestUtils.setField(filter, "publicOrigin", ORIGIN);
    return filter;
  }

  private MockHttpServletResponse call(String path, String token, String origin, String bearer)
      throws Exception {
    var request = new MockHttpServletRequest("POST", path);
    request.setRemoteAddr("172.19.0.6");
    request.setServerName("finance.example.test");
    if (token != null) request.addHeader("Cf-Access-Jwt-Assertion", token);
    if (origin != null) request.addHeader("Origin", origin);
    if (bearer != null) request.addHeader("Authorization", bearer);
    var response = new MockHttpServletResponse();
    filter().doFilter(request, response, (req, res) -> res.getWriter().write("passed"));
    return response;
  }

  @Test
  void rejectsMissingAndInvalidTokensEvenForDirectOriginRequests() throws Exception {
    assertThat(call("/api/finances/accounts", null, null, null).getStatus()).isEqualTo(401);
    assertThat(call("/api/finances/accounts", "forged", ORIGIN, null).getStatus()).isEqualTo(401);
  }

  @Test
  void acceptsAuthenticatedSameOriginRequestsAndRejectsCrossOriginWrites() throws Exception {
    var accepted = call("/api/finances/accounts", "valid-token", ORIGIN, null);
    assertThat(accepted.getContentAsString()).isEqualTo("passed");
    assertThat(accepted.getHeader("Cache-Control")).isEqualTo("no-store");
    assertThat(call("/api/finances/accounts", "valid-token", "https://evil.test", null).getStatus())
        .isEqualTo(403);
    assertThat(call("/api/finances/accounts", "valid-token", "null", null).getStatus())
        .isEqualTo(403);
  }

  @Test
  void mcpRequiresBothCloudflareAndItsOwnBearerKey() throws Exception {
    String bearer = "Bearer a-long-test-mcp-key-with-at-least-32-characters";
    assertThat(call("/mcp/finances", null, null, bearer).getStatus()).isEqualTo(401);
    assertThat(call("/mcp/finances", "valid-token", null, null).getStatus()).isEqualTo(401);
    assertThat(call("/mcp/finances", "valid-token", null, "Bearer wrong").getStatus())
        .isEqualTo(401);
    assertThat(call("/mcp/finances", "valid-token", null, bearer).getContentAsString())
        .isEqualTo("passed");
  }

  @Test
  void credentialRevealUsesTheSameProtectedBrowserBoundary() throws Exception {
    String path = "/api/finances/configuration/mcp-key";
    assertThat(call(path, null, null, null).getStatus()).isEqualTo(401);
    assertThat(call(path, "forged", ORIGIN, null).getStatus()).isEqualTo(401);
    assertThat(call(path, "valid-token", "https://evil.test", null).getStatus()).isEqualTo(403);
    var accepted = call(path, "valid-token", ORIGIN, null);
    assertThat(accepted.getContentAsString()).isEqualTo("passed");
    assertThat(accepted.getHeader("Cache-Control")).isEqualTo("no-store");
  }

  @Test
  void validatesApplicationIssuerAndExpiry() {
    var validator = FinanceCloudflareAccessConfiguration.validators(ISSUER, "application");
    assertThat(
            validator
                .validate(jwt("application", ISSUER, Instant.now().plusSeconds(300)))
                .hasErrors())
        .isFalse();
    assertThat(
            validator
                .validate(jwt("other-app", ISSUER, Instant.now().plusSeconds(300)))
                .hasErrors())
        .isTrue();
    assertThat(
            validator
                .validate(
                    jwt("application", "https://other.example", Instant.now().plusSeconds(300)))
                .hasErrors())
        .isTrue();
    assertThat(
            validator
                .validate(jwt("application", ISSUER, Instant.now().minusSeconds(300)))
                .hasErrors())
        .isTrue();
    var withoutExpiry =
        Jwt.withTokenValue("test")
            .header("alg", "RS256")
            .issuer(ISSUER)
            .audience(List.of("application"))
            .build();
    assertThat(validator.validate(withoutExpiry).hasErrors()).isTrue();
  }

  private static Jwt jwt(String audience, String issuer, Instant expiry) {
    return Jwt.withTokenValue("test")
        .header("alg", "RS256")
        .issuer(issuer)
        .audience(List.of(audience))
        .issuedAt(Instant.now().minusSeconds(600))
        .expiresAt(expiry)
        .build();
  }
}
