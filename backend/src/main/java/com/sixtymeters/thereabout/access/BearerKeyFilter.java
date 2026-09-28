package com.sixtymeters.thereabout.access;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.function.Predicate;

/**
 * Grants a role for an application-managed bearer credential. With {@code requireIdentity}, the key adds
 * to an already authenticated login instead of replacing it (MCP needs Cloudflare and its own key).
 */
public class BearerKeyFilter extends OncePerRequestFilter {
    private final Predicate<String> accepts;
    private final GrantedAuthority role;
    private final boolean requireIdentity;

    public BearerKeyFilter(Predicate<String> accepts, String role, boolean requireIdentity) {
        this.accepts = accepts;
        this.role = new SimpleGrantedAuthority(role);
        this.requireIdentity = requireIdentity;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var current = SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
        if ((current != null || !requireIdentity) && accepts.test(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            var authorities = new ArrayList<GrantedAuthority>();
            if (current != null) authorities.addAll(current.getAuthorities());
            authorities.add(role);
            AccessPrincipal.authenticate(current != null ? current.getPrincipal() : "api-key", authorities);
        }
        chain.doFilter(request, response);
    }
}
