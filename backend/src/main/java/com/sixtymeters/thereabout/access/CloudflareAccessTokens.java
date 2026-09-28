package com.sixtymeters.thereabout.access;

import java.net.URI;
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

/** Common signature, issuer, application audience and lifetime verification for Access JWTs. */
public final class CloudflareAccessTokens {
    private static final Duration KEY_SERVICE_TIMEOUT = Duration.ofSeconds(5);

    private CloudflareAccessTokens() {}

    public static JwtDecoder decoder(String configuredIssuer, String audience) {
        String issuer = configuredIssuer.replaceAll("/+$", "");
        URI uri = URI.create(issuer);
        Assert.isTrue("https".equals(uri.getScheme()) && uri.getHost() != null, "Cloudflare Access issuer must be HTTPS");
        Assert.hasText(audience, "Cloudflare Access audience is required");
        // Every request is verified, so a hanging key service must not hold request threads.
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(KEY_SERVICE_TIMEOUT);
        requests.setReadTimeout(KEY_SERVICE_TIMEOUT);
        var decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/cdn-cgi/access/certs")
                .restOperations(new RestTemplate(requests)).build();
        decoder.setJwtValidator(validators(issuer, audience));
        return decoder;
    }

    public static OAuth2TokenValidator<Jwt> validators(String issuer, String audience) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<java.util.List<String>>("aud", value -> value != null && value.contains(audience)),
                new JwtClaimValidator<java.time.Instant>("exp", value -> value != null));
    }
}
