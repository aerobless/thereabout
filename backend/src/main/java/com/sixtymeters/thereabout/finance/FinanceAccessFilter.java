package com.sixtymeters.thereabout.finance;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Explicit local or authenticated proxy boundary, independent of the permitAll application chain.
 */
@Component
@Order(-200)
public class FinanceAccessFilter extends OncePerRequestFilter {
  @Value("${thereabout.finances.enabled:false}")
  private boolean enabled;

  @Value("${thereabout.finances.mcp-key:}")
  private String key;

  @Value("${thereabout.finances.access-mode:local}")
  private String accessMode;

  @Value("${thereabout.finances.public-origin:}")
  private String publicOrigin;

  private final ObjectProvider<JwtDecoder> accessTokens;

  public FinanceAccessFilter(ObjectProvider<JwtDecoder> accessTokens) {
    this.accessTokens = accessTokens;
  }

  private boolean loopback(String host) {
    return host != null
        && Set.of("localhost", "127.0.0.1", "::1", "[::1]", "0:0:0:0:0:0:0:1").contains(host);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    if (!path.startsWith("/api/finances") && !path.startsWith("/mcp/finances")) {
      chain.doFilter(request, response);
      return;
    }
    if (!enabled) {
      response.sendError(404);
      return;
    }
    boolean local = "local".equals(accessMode);
    if (local && (!loopback(request.getRemoteAddr()) || !loopback(request.getServerName()))) {
      response.sendError(403);
      return;
    }
    if (!local && !"cloudflare".equals(accessMode)) {
      response.sendError(403);
      return;
    }
    if (!local && !validAccessToken(request.getHeader("Cf-Access-Jwt-Assertion"))) {
      response.sendError(401);
      return;
    }
    String origin = request.getHeader("Origin");
    if (origin != null) {
      try {
        URI uri = URI.create(origin);
        boolean allowed =
            local
                ? loopback(uri.getHost())
                    && Set.of(4200, 9050).contains(uri.getPort())
                    && "http".equals(uri.getScheme())
                : !publicOrigin.isBlank() && publicOrigin.equals(origin);
        if (!allowed) {
          response.sendError(403);
          return;
        }
      } catch (IllegalArgumentException e) {
        response.sendError(403);
        return;
      }
    }
    if (path.startsWith("/mcp/finances")) {
      String auth = request.getHeader("Authorization");
      if (key.length() < 32
          || auth == null
          || !MessageDigest.isEqual(
              ("Bearer " + key).getBytes(StandardCharsets.UTF_8),
              auth.getBytes(StandardCharsets.UTF_8))) {
        response.setHeader("WWW-Authenticate", "Bearer");
        response.sendError(401);
        return;
      }
    }
    response.setHeader("Cache-Control", "no-store");
    chain.doFilter(request, response);
  }

  private boolean validAccessToken(String token) {
    JwtDecoder decoder = accessTokens.getIfAvailable();
    if (decoder == null || token == null || token.isBlank()) return false;
    try {
      decoder.decode(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }
}
