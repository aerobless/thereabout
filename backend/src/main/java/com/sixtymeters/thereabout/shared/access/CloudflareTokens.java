package com.sixtymeters.thereabout.shared.access;

import java.net.URI;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.util.Assert;

public final class CloudflareTokens {
    private CloudflareTokens() {}

    public static JwtDecoder decoder(String issuer, String audience) {
        URI uri = URI.create(issuer);
        Assert.isTrue("https".equals(uri.getScheme()) && uri.getHost() != null
                && uri.getUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null,
                "Cloudflare issuer must be an HTTPS URL");
        Assert.hasText(audience, "Cloudflare audience is required");
        var decoder = NimbusJwtDecoder.withJwkSetUri(issuer.replaceAll("/$", "") + "/cdn-cgi/access/certs").build();
        decoder.setJwtValidator(validators(issuer, audience));
        return decoder;
    }

    public static OAuth2TokenValidator<Jwt> validators(String issuer, String audience) {
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<java.util.List<String>>("aud", value -> value != null && value.contains(audience)),
                new JwtClaimValidator<java.time.Instant>("exp", value -> value != null));
    }
}
