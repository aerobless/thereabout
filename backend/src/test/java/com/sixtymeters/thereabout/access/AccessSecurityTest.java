package com.sixtymeters.thereabout.access;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sixtymeters.thereabout.client.service.ConfigurationService;
import com.sixtymeters.thereabout.communication.data.IdentityEntity;
import com.sixtymeters.thereabout.communication.data.IdentityRepository;
import com.sixtymeters.thereabout.communication.service.IdentityUserService;
import com.sixtymeters.thereabout.finance.service.FinanceMcpKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The security boundary over real HTTP, including the raw paths a servlet container decodes before routing. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "thereabout.access.mode=cloudflare",
        "thereabout.users.access-issuer=" + AccessSecurityTest.ISSUER,
        "thereabout.users.access-audience=app",
        "thereabout.finances.access-mode=cloudflare",
        "thereabout.finances.access-issuer=" + AccessSecurityTest.ISSUER,
        "thereabout.finances.access-audience=finance-app"})
@ActiveProfiles("test")
class AccessSecurityTest {
    static final String ISSUER = "https://team.cloudflareaccess.com";
    static final RSAKey KEY = key();

    @TestBean JwtDecoder userAccessTokenDecoder;
    @TestBean JwtDecoder financeAccessTokenDecoder;

    static JwtDecoder userAccessTokenDecoder() { return decoder("app"); }
    static JwtDecoder financeAccessTokenDecoder() { return decoder("finance-app"); }

    @LocalServerPort int port;
    @Autowired IdentityRepository identities;
    @Autowired IdentityUserService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired ConfigurationService configuration;
    @Autowired FinanceMcpKeyService mcpKeys;

    final HttpClient http = HttpClient.newHttpClient();
    String user;
    String admin;

    @BeforeEach
    void users() {
        user = createUser(false);
        admin = createUser(true);
    }

    @Test
    void applicationDataRequiresAVerifiedThereaboutUser() throws Exception {
        assertThat(send(get("/backend/api/v1/identity")).statusCode()).isEqualTo(401);
        assertThat(send(get("/backend/api/v1/identity").header(CloudflareAccessFilter.HEADER, token("app", user, true))).statusCode()).isEqualTo(401);
        assertThat(send(get("/backend/api/v1/identity").header(CloudflareAccessFilter.HEADER, token("app", user, false))).statusCode()).isEqualTo(200);
        String unlinked = UUID.randomUUID() + "@example.test";
        assertThat(send(get("/backend/api/v1/identity").header(CloudflareAccessFilter.HEADER, token("app", unlinked, false))).statusCode()).isEqualTo(403);
        assertThat(send(get("/v3/api-docs")).statusCode()).isEqualTo(401);
    }

    @Test
    void currentUserReportsTheVerifiedLoginWithoutRequiringAUser() throws Exception {
        assertThat(send(get("/backend/api/v1/current-user")).body()).contains("missing_token");
        assertThat(send(get("/backend/api/v1/current-user").header(CloudflareAccessFilter.HEADER, token("app", UUID.randomUUID() + "@example.test", false))).body())
                .contains("unlinked");
        assertThat(send(get("/backend/api/v1/current-user").header(CloudflareAccessFilter.HEADER, token("app", user, false))).body())
                .contains("resolved").contains("\"isAdmin\":false");
        assertThat(send(get("/backend/api/v1/current-user").header(CloudflareAccessFilter.HEADER, token("app", admin, false))).body())
                .contains("\"isAdmin\":true");
    }

    @Test
    void browserWritesRequireTheCsrfToken() throws Exception {
        String login = token("app", user, false);
        String body = "{\"id\":0,\"shortName\":\"csrf-" + UUID.randomUUID().toString().substring(0, 8) + "\"}";
        assertThat(send(json("/backend/api/v1/identity", body).header(CloudflareAccessFilter.HEADER, login)).statusCode()).isEqualTo(403);
        String csrf = send(get("/backend/api/v1/current-user")).headers().allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow()
                .split(";")[0].substring("XSRF-TOKEN=".length());
        assertThat(send(json("/backend/api/v1/identity", body).header(CloudflareAccessFilter.HEADER, login)
                .header("Cookie", "XSRF-TOKEN=" + csrf).header("X-XSRF-TOKEN", csrf)).statusCode()).isEqualTo(200);
    }

    @Test
    void onlyAdministratorsCreateUsers() throws Exception {
        long person = identities.save(IdentityEntity.builder().shortName("person-" + UUID.randomUUID().toString().substring(0, 8)).build()).getId();
        String csrf = UUID.randomUUID().toString();
        String body = "{\"email\":\"" + UUID.randomUUID() + "@example.test\"}";
        var request = json("/backend/api/v1/identity/" + person + "/user", body).header("Cookie", "XSRF-TOKEN=" + csrf).header("X-XSRF-TOKEN", csrf);
        assertThat(send(request.copy().header(CloudflareAccessFilter.HEADER, token("app", user, false))).statusCode()).isEqualTo(403);
        assertThat(send(request.copy().header(CloudflareAccessFilter.HEADER, token("app", admin, false))).statusCode()).isEqualTo(200);
    }

    @Test
    void financeRequiresItsOwnAudienceEvenForEncodedPaths() throws Exception {
        for (String path : new String[]{"/api/finances/accounts", "/api/%66inances/accounts"}) {
            assertThat(send(get(path)).statusCode()).as(path).isEqualTo(401);
            assertThat(send(get(path).header(CloudflareAccessFilter.HEADER, token("app", user, false))).statusCode()).as(path).isEqualTo(401);
            assertThat(send(get(path).header(CloudflareAccessFilter.HEADER, token("finance-app", user, false))).statusCode()).as(path).isEqualTo(200);
        }
    }

    @Test
    void ingestionUsesTheApiKeyInsteadOfCloudflare() throws Exception {
        String key = configuration.getThereaboutApiKey();
        for (String path : new String[]{"/backend/api/v1/ingest/location/geojson"}) {
            var location = json(path, "{\"locations\":[]}");
            assertThat(send(location.copy()).statusCode()).as(path).isEqualTo(401);
            assertThat(send(location.copy().header("Authorization", "Bearer wrong")).statusCode()).as(path).isEqualTo(401);
            assertThat(send(location.copy().header("Authorization", "Bearer " + key)).statusCode()).as(path).isEqualTo(200);
            assertThat(send(location.copy().header("Authorization", key)).statusCode()).as(path).isEqualTo(200);
        }
        for (String path : new String[]{"/backend/api/v1/ingest/health"}) {
            assertThat(send(json(path, "{}")).statusCode()).as(path).isEqualTo(401);
            assertThat(send(json(path, "{}").header("Authorization", "Bearer " + key)).statusCode()).as(path).isEqualTo(400);
        }
        // The key is limited to ingestion: reads still need a Cloudflare user.
        assertThat(send(get("/backend/api/v1/location").header("Authorization", "Bearer " + key)).statusCode()).isEqualTo(401);
        assertThat(send(get("/backend/api/v1/health/data").header("Authorization", "Bearer " + key)).statusCode()).isEqualTo(401);
        for (String path : new String[]{"/backend/api/v1/ingest/calendar/google/notifications"}) {
            assertThat(send(json(path, "")).statusCode()).as(path).isNotIn(401, 403);
        }
        // The former paths are ordinary browser routes again: the key alone is refused (CSRF before login).
        assertThat(send(json("/backend/api/v1/health", "{}").header("Authorization", "Bearer " + key)).statusCode()).isIn(401, 403);
        assertThat(send(json("/backend/api/v1/location/geojson", "{\"locations\":[]}").header("Authorization", "Bearer " + key)).statusCode()).isIn(401, 403);
        // Nothing else is reachable under the bypassed prefix, not even for users.
        assertThat(send(get("/backend/api/v1/ingest/health").header(CloudflareAccessFilter.HEADER, token("app", user, false))).statusCode()).isEqualTo(403);
    }

    @Test
    void mcpRequiresCloudflareAndItsOwnKey() throws Exception {
        String initialize = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\","
                + "\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}";
        var mcp = json("/mcp/finances", initialize).header("Accept", "application/json, text/event-stream");
        String bearer = "Bearer " + mcpKeys.getKey();
        var withoutCloudflare = send(mcp.copy().header("Authorization", bearer));
        assertThat(withoutCloudflare.statusCode()).isEqualTo(401);
        assertThat(withoutCloudflare.headers().firstValue("WWW-Authenticate")).contains("Bearer");
        String serviceToken = token("finance-app", null, false);
        assertThat(send(mcp.copy().header(CloudflareAccessFilter.HEADER, serviceToken)).statusCode()).isEqualTo(401);
        assertThat(send(mcp.copy().header(CloudflareAccessFilter.HEADER, token("app", user, false)).header("Authorization", bearer)).statusCode()).isEqualTo(401);
        assertThat(send(mcp.copy().header(CloudflareAccessFilter.HEADER, serviceToken).header("Authorization", bearer)).statusCode()).isEqualTo(200);
    }

    @Test
    void staticFrontendAndHealthStayReachable() throws Exception {
        assertThat(send(get("/actuator/health")).statusCode()).isNotIn(401, 403);
        assertThat(send(get("/locationhistory")).statusCode()).isNotIn(401, 403);
    }

    private String createUser(boolean isAdmin) {
        String email = UUID.randomUUID() + "@example.test";
        long id = identities.save(IdentityEntity.builder().shortName("access-" + UUID.randomUUID().toString().substring(0, 8)).build()).getId();
        users.createUser(id, email);
        jdbc.update("update identity set is_admin=? where id=?", isAdmin, id);
        return email;
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
    }

    private HttpRequest.Builder json(String path, String body) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String token(String audience, String email, boolean forged) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(ISSUER).audience(audience)
                .issueTime(Date.from(Instant.now().minusSeconds(60))).expirationTime(Date.from(Instant.now().plusSeconds(600)));
        if (email != null) claims.claim("email", email);
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner(forged ? new RSAKeyGenerator(2048).generate() : KEY));
        return jwt.serialize();
    }

    private static JwtDecoder decoder(String audience) {
        try {
            var decoder = NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
            decoder.setJwtValidator(CloudflareAccessTokens.validators(ISSUER, audience));
            return decoder;
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey key() {
        try {
            return new RSAKeyGenerator(2048).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
