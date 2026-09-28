package com.sixtymeters.thereabout.access;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Local development without Cloudflare: only this machine acts as the local administrator. The Host
 * check rejects DNS rebinding, where a foreign page resolves its own name to the loopback address.
 */
public class LocalAccessFilter extends OncePerRequestFilter {
    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "::1", "[::1]", "0:0:0:0:0:0:0:1");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication() == null
                && LOOPBACK.contains(request.getRemoteAddr()) && LOOPBACK.contains(request.getServerName())) {
            AccessPrincipal.authenticate(AccessPrincipal.LOCAL, AccessPrincipal.roles(true, true));
        }
        chain.doFilter(request, response);
    }
}
