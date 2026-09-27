package com.sixtymeters.thereabout.users;

import com.sixtymeters.thereabout.config.ThereaboutException;
import com.sixtymeters.thereabout.shared.access.CloudflareTokens;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
public class CloudflareUserAccess {
    public enum State { VERIFIED, NO_IDENTITY, INVALID_IDENTITY, NOT_CONFIGURED, UNAVAILABLE }
    public record Result(State state, String email) {}
    private final String mode;
    private final String publicOrigin;
    private final boolean development;
    private final JwtDecoder decoder;

    @org.springframework.beans.factory.annotation.Autowired
    public CloudflareUserAccess(
            @Value("${thereabout.users.access-mode:${thereabout.finances.access-mode:disabled}}") String mode,
            @Value("${thereabout.users.access-issuer:${thereabout.finances.access-issuer:}}") String issuer,
            @Value("${thereabout.users.access-audience:${thereabout.finances.access-audience:}}") String audience,
            @Value("${thereabout.users.public-origin:${thereabout.finances.public-origin:}}") String publicOrigin,
            Environment environment) {
        this(mode, publicOrigin, environment.acceptsProfiles(Profiles.of("development", "finance-local", "test")),
                "cloudflare".equals(mode) ? CloudflareTokens.decoder(issuer, audience) : null);
    }

    CloudflareUserAccess(String mode, String publicOrigin, boolean development, JwtDecoder decoder) {
        this.mode = mode;
        this.publicOrigin = publicOrigin;
        this.development = development;
        this.decoder = decoder;
    }

    public Result resolve(HttpServletRequest request) {
        if (!"cloudflare".equals(mode)) return new Result(State.NOT_CONFIGURED, null);
        String token = request.getHeader("Cf-Access-Jwt-Assertion");
        if (token == null || token.isBlank()) return new Result(State.NO_IDENTITY, null);
        try {
            var jwt = decoder.decode(token);
            String email = jwt.getClaimAsString("email");
            return email == null || email.isBlank() ? new Result(State.NO_IDENTITY, null) : new Result(State.VERIFIED, email);
        } catch (BadJwtException | IllegalArgumentException e) {
            return new Result(State.INVALID_IDENTITY, null);
        } catch (JwtException e) {
            // E.g. the signing-key endpoint is unavailable. Never expose the token or decoder error.
            return new Result(State.UNAVAILABLE, null);
        }
    }

    public void requireUserCreationAccess(HttpServletRequest request) {
        boolean local = "local".equals(mode) && development && loopback(request.getRemoteAddr()) && loopback(request.getServerName());
        if (!local) {
            var result = resolve(request);
            if (result.state() != State.VERIFIED) {
                throw new ThereaboutException(result.state() == State.UNAVAILABLE ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.UNAUTHORIZED,
                        "A verified Cloudflare login is required.");
            }
        }
        String origin = request.getHeader("Origin");
        if (origin != null && !allowedOrigin(origin, local)) {
            throw new ThereaboutException(HttpStatus.FORBIDDEN, "Request origin is not allowed.");
        }
    }

    private boolean allowedOrigin(String origin, boolean local) {
        if (!local) return !publicOrigin.isBlank() && publicOrigin.equals(origin);
        try {
            URI uri = URI.create(origin);
            return "http".equals(uri.getScheme()) && loopback(uri.getHost()) && Set.of(4200, 9050).contains(uri.getPort());
        } catch (IllegalArgumentException e) { return false; }
    }

    private boolean loopback(String host) {
        return host != null && Set.of("localhost", "127.0.0.1", "::1", "[::1]", "0:0:0:0:0:0:0:1").contains(host);
    }
}
