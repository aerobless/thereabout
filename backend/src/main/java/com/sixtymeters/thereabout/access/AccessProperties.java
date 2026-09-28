package com.sixtymeters.thereabout.access;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * How requests are authenticated. {@code local} admits only this machine and is the default, so an
 * unconfigured installation never opens up. {@code cloudflare} requires the assertion of the one Access
 * application identified by its team issuer and audience; both must be configured, never taken from a token.
 */
@ConfigurationProperties("thereabout.access")
public record AccessProperties(Mode mode, Cloudflare cloudflare) {
    public enum Mode { LOCAL, CLOUDFLARE }

    public record Cloudflare(String issuer, String audience) {
        public boolean configured() {
            return StringUtils.hasText(issuer) || StringUtils.hasText(audience);
        }
    }

    public AccessProperties {
        if (mode == null) throw new IllegalStateException("thereabout.access.mode must be local or cloudflare");
        if (cloudflare == null) cloudflare = new Cloudflare(null, null);
        if (mode == Mode.CLOUDFLARE && !cloudflare.configured()) {
            throw new IllegalStateException("Cloudflare access requires thereabout.access.cloudflare.issuer and audience");
        }
    }
}
