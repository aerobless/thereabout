package com.sixtymeters.thereabout.config;

import com.sixtymeters.thereabout.access.BearerKeyFilter;
import com.sixtymeters.thereabout.access.CloudflareAccessFilter;
import com.sixtymeters.thereabout.access.CloudflareUsers;
import com.sixtymeters.thereabout.access.CsrfCookieFilter;
import com.sixtymeters.thereabout.access.LocalAccessFilter;
import com.sixtymeters.thereabout.client.service.ConfigurationService;
import com.sixtymeters.thereabout.finance.service.FinanceMcpKeyService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static com.sixtymeters.thereabout.access.AccessPrincipal.ADMIN;
import static com.sixtymeters.thereabout.access.AccessPrincipal.USER;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

/**
 * Cloudflare Access authenticates people; Thereabout verifies its signed assertion on every request and
 * authorizes. Browser requests carry that login implicitly (the Access cookie), so they need CSRF tokens.
 * Clients outside Access use their own application-managed keys on a few exact endpoints.
 * Matchers use the decoded, normalized path, the same path Spring MVC routes on.
 */
@Configuration
public class SecurityConfiguration {
    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();
    private static final RequestMatcher GOOGLE_NOTIFICATIONS = PATHS.matcher(POST, "/backend/api/v1/calendar/google/notifications");
    private static final RequestMatcher LOCATION_INGESTION = PATHS.matcher(POST, "/backend/api/v1/location/geojson");
    private static final RequestMatcher HEALTH_INGESTION = PATHS.matcher(POST, "/backend/api/v1/health");

    enum Mode { LOCAL, CLOUDFLARE }

    private final Mode mode;
    private final JwtDecoder userDecoder;
    private final JwtDecoder financeDecoder;
    private final CloudflareUsers users;

    SecurityConfiguration(@Value("${thereabout.access.mode:${thereabout.finances.access-mode:local}}") String mode,
                          @Qualifier("userAccessTokenDecoder") ObjectProvider<JwtDecoder> userDecoder,
                          @Qualifier("financeAccessTokenDecoder") ObjectProvider<JwtDecoder> financeDecoder,
                          CloudflareUsers users) {
        this.mode = switch (mode) {
            case "local" -> Mode.LOCAL;
            case "cloudflare" -> Mode.CLOUDFLARE;
            default -> throw new IllegalStateException("thereabout.access.mode must be local or cloudflare");
        };
        this.userDecoder = userDecoder.getIfAvailable();
        if (this.mode == Mode.CLOUDFLARE && this.userDecoder == null) {
            throw new IllegalStateException("Cloudflare access requires thereabout.users.access-issuer and access-audience");
        }
        this.financeDecoder = financeDecoder.getIfAvailable(() -> this.userDecoder);
        this.users = users;
    }

    /** Google authenticates its callback with the channel token checked by the calendar service. */
    @Bean
    @Order(1)
    SecurityFilterChain ingestion(HttpSecurity http, ConfigurationService configuration) throws Exception {
        machine(http.securityMatcher(new OrRequestMatcher(LOCATION_INGESTION, HEALTH_INGESTION, GOOGLE_NOTIFICATIONS)))
                .addFilterBefore(new BearerKeyFilter(configuration::acceptsThereaboutApiKey, "ROLE_INGEST", false),
                        AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(rules -> rules
                        .requestMatchers(GOOGLE_NOTIFICATIONS).permitAll()
                        .anyRequest().hasRole("INGEST"))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized(false)));
        return http.build();
    }

    /** MCP requires both an admitted Cloudflare login (or local development) and its own bearer key. */
    @Bean
    @Order(2)
    SecurityFilterChain financeMcp(HttpSecurity http, FinanceMcpKeyService mcpKeys) throws Exception {
        identify(machine(http.securityMatcher(new OrRequestMatcher(PATHS.matcher("/mcp/finances"), PATHS.matcher("/mcp/finances/**")))), financeDecoder)
                .addFilterBefore(new BearerKeyFilter(mcpKeys::matchesAuthorization, "ROLE_MCP", true),
                        AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(rules -> rules.anyRequest().hasRole("MCP"))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized(true))
                        .accessDeniedHandler((request, response, denied) -> unauthorized(true).commence(request, response, null)));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain finance(HttpSecurity http) throws Exception {
        browser(http.securityMatcher(PATHS.matcher("/api/finances/**")), financeDecoder)
                .authorizeHttpRequests(rules -> rules.anyRequest().hasAuthority(USER));
        return http.build();
    }

    /** Static frontend files hold no data; everything that does requires a Thereabout user. */
    @Bean
    @Order(4)
    SecurityFilterChain application(HttpSecurity http) throws Exception {
        browser(http, userDecoder).authorizeHttpRequests(rules -> rules
                .requestMatchers(PATHS.matcher(GET, "/backend/api/v1/current-user")).permitAll()
                .requestMatchers(PATHS.matcher(POST, "/backend/api/v1/identity/{id}/user")).hasAuthority(ADMIN)
                .requestMatchers(PATHS.matcher("/actuator/health"), PATHS.matcher("/actuator/health/**")).permitAll()
                .requestMatchers(PATHS.matcher("/backend/**"), PATHS.matcher("/api/**"), PATHS.matcher("/mcp/**"),
                        PATHS.matcher("/actuator/**"), PATHS.matcher("/v3/api-docs/**"), PATHS.matcher("/swagger-ui/**"),
                        PATHS.matcher("/swagger-ui.html")).hasAuthority(USER)
                .anyRequest().permitAll());
        return http.build();
    }

    private HttpSecurity browser(HttpSecurity http, JwtDecoder decoder) throws Exception {
        var tokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
        tokens.setCookieCustomizer(cookie -> cookie.secure(mode == Mode.CLOUDFLARE).sameSite("Lax"));
        return identify(stateless(http), decoder)
                .csrf(csrf -> csrf.spa().csrfTokenRepository(tokens))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.disable())
                        .contentSecurityPolicy(policy -> policy.policyDirectives(
                                "frame-ancestors 'self' http://localhost:* https://localhost:* https://family.w1nter.com")))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized(false)));
    }

    private HttpSecurity identify(HttpSecurity http, JwtDecoder decoder) {
        if (decoder != null) http.addFilterBefore(new CloudflareAccessFilter(decoder, users), AnonymousAuthenticationFilter.class);
        if (mode == Mode.LOCAL) http.addFilterBefore(new LocalAccessFilter(), AnonymousAuthenticationFilter.class);
        return http;
    }

    /** Every request carries its own credentials, so no session is needed. */
    private static HttpSecurity stateless(HttpSecurity http) throws Exception {
        return http.sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }

    /** Explicit keys are never sent automatically by a browser, so these endpoints need no CSRF token. */
    private static HttpSecurity machine(HttpSecurity http) throws Exception {
        return stateless(http).csrf(csrf -> csrf.disable());
    }

    /** A Cloudflare key-service outage is reported as unavailable rather than as a bad login. */
    private static AuthenticationEntryPoint unauthorized(boolean bearer) {
        return (request, response, failure) -> {
            if (request.getAttribute(CloudflareAccessFilter.STATUS) == CloudflareAccessFilter.Status.VERIFICATION_UNAVAILABLE) {
                response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value());
                return;
            }
            if (bearer) response.setHeader("WWW-Authenticate", "Bearer");
            response.sendError(HttpStatus.UNAUTHORIZED.value());
        };
    }
}
