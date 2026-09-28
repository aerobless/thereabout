package com.sixtymeters.thereabout.access;

import com.sixtymeters.thereabout.communication.service.CloudflareEmail;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates the assertion Cloudflare Access adds to every request it admits. Plain email headers are
 * never trusted. Authorization rules decide what the login may use; the outcome is kept for current-user.
 */
public class CloudflareAccessFilter extends OncePerRequestFilter {
    public static final String HEADER = "Cf-Access-Jwt-Assertion";
    public static final String STATUS = CloudflareAccessFilter.class.getName() + ".status";

    public enum Status { MISSING_TOKEN, INVALID_TOKEN, VERIFICATION_UNAVAILABLE, VERIFIED }

    private final JwtDecoder decoder;
    private final CloudflareUsers users;

    public CloudflareAccessFilter(JwtDecoder decoder, CloudflareUsers users) {
        this.decoder = decoder;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        request.setAttribute(STATUS, authenticate(request.getHeader(HEADER)));
        chain.doFilter(request, response);
    }

    private Status authenticate(String token) {
        if (token == null || token.isBlank()) return Status.MISSING_TOKEN;
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (BadJwtException | IllegalArgumentException e) {
            return Status.INVALID_TOKEN;
        } catch (JwtException e) {
            // Key retrieval or verification infrastructure failed. Never expose exception text or tokens.
            return Status.VERIFICATION_UNAVAILABLE;
        }
        String email = email(jwt);
        var user = email == null ? java.util.Optional.<CloudflareUsers.User>empty() : users.resolve(email);
        var principal = new AccessPrincipal(email, user.map(CloudflareUsers.User::identityId).orElse(null),
                user.map(CloudflareUsers.User::displayName).orElse(null), false);
        AccessPrincipal.authenticate(principal,
                AccessPrincipal.roles(user.isPresent(), user.map(CloudflareUsers.User::admin).orElse(false)));
        return Status.VERIFIED;
    }

    /** Service tokens carry no email; they authenticate but never resolve a user. */
    private static String email(Jwt jwt) {
        try {
            return CloudflareEmail.normalize(jwt.getClaimAsString("email"));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
