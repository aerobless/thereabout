package com.sixtymeters.thereabout.access;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sixtymeters.thereabout.generated.model.GenCurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.*;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.sixtymeters.thereabout.generated.model.GenCurrentUser.StatusEnum.*;

class CloudflareAccessFilterTest {
    static final String ISSUER = "https://team.cloudflareaccess.com";
    final com.nimbusds.jose.jwk.RSAKey key = new RSAKeyGenerator(2048).generate();
    final CloudflareUsers users = mock(CloudflareUsers.class);
    JwtDecoder decoder;
    CloudflareAccessFilterTest() throws Exception {}

    @BeforeEach void setup() throws Exception {
        var nimbus = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        nimbus.setJwtValidator(CloudflareAccessTokens.validators(ISSUER, "app"));
        decoder = nimbus;
        when(users.resolve(any())).thenReturn(Optional.empty());
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    String token(String issuer, String audience, Instant expiry, String email, boolean forged) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience).issueTime(Date.from(Instant.now().minusSeconds(600)))
                .claim("email", email);
        if (expiry != null) claims.expirationTime(Date.from(expiry));
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner(forged ? new RSAKeyGenerator(2048).generate() : key));
        return jwt.serialize();
    }
    String valid(String email) throws Exception { return token(ISSUER, "app", Instant.now().plusSeconds(600), email, false); }

    /** Runs the filter and reports what current-user says inside the authenticated request. */
    GenCurrentUser currentUser(MockHttpServletRequest request, JwtDecoder decoder) throws Exception {
        var result = new AtomicReference<GenCurrentUser>();
        var controller = new CurrentUserController(request);
        var respond = (jakarta.servlet.FilterChain) (req, res) -> {
            var response = controller.getCurrentUser();
            assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
            result.set(response.getBody());
        };
        if (decoder == null) respond.doFilter(request, new MockHttpServletResponse());
        else new CloudflareAccessFilter(decoder, users).doFilter(request, new MockHttpServletResponse(), respond);
        var body = result.get();
        if (body.getStatus() != RESOLVED) {
            assertThat(body.getIdentityId()).isNull();
            assertThat(body.getDisplayName()).isNull();
            assertThat(body.getIsAdmin()).isNull();
        }
        return body;
    }
    GenCurrentUser currentUser(String token) throws Exception {
        var request = new MockHttpServletRequest("GET", "/backend/api/v1/current-user");
        if (token != null) request.addHeader(CloudflareAccessFilter.HEADER, token);
        return currentUser(request, decoder);
    }

    @Test void knownEmailUsesStablePersonIdShortNameAndRole() throws Exception {
        when(users.resolve("heidi@example.test")).thenReturn(Optional.of(new CloudflareUsers.User(42L, "Heidi", false)));
        var user = currentUser(valid(" Heidi@Example.test "));
        assertThat(user.getStatus()).isEqualTo(RESOLVED);
        assertThat(user.getIdentityId()).isEqualTo(42L);
        assertThat(user.getDisplayName()).isEqualTo("Heidi");
        assertThat(user.getIsAdmin()).isFalse();
        when(users.resolve("theo@example.test")).thenReturn(Optional.of(new CloudflareUsers.User(7L, "Theo", true)));
        assertThat(currentUser(valid("theo@example.test")).getIsAdmin()).isTrue();
    }
    @Test void validUnknownLoginIsUnlinkedAndHasNoRole() throws Exception {
        var request = new MockHttpServletRequest("GET", "/backend/api/v1/identity");
        request.addHeader(CloudflareAccessFilter.HEADER, valid("unknown@example.test"));
        new CloudflareAccessFilter(decoder, users).doFilter(request, new MockHttpServletResponse(), (req, res) ->
                assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).isEmpty());
        assertThat(currentUser(valid("unknown@example.test")).getStatus()).isEqualTo(UNLINKED);
    }
    @Test void plainEmailHeaderDoesNotAuthenticate() throws Exception {
        var request = new MockHttpServletRequest("GET", "/backend/api/v1/current-user");
        request.addHeader("Cf-Access-Authenticated-User-Email", "heidi@example.test");
        assertThat(currentUser(request, decoder).getStatus()).isEqualTo(MISSING_TOKEN);
        verifyNoInteractions(users);
    }
    @Test void invalidSignatureIssuerAudienceExpiryOrEmailNeverResolve() throws Exception {
        Instant future = Instant.now().plusSeconds(600);
        for (String invalid : new String[]{"malformed", token(ISSUER, "app", future, "heidi@example.test", true),
                token("https://wrong.example", "app", future, "heidi@example.test", false),
                token(ISSUER, "wrong", future, "heidi@example.test", false),
                token(ISSUER, "app", Instant.now().minusSeconds(300), "heidi@example.test", false),
                token(ISSUER, "app", null, "heidi@example.test", false),
                token(ISSUER, "app", future, null, false)}) {
            assertThat(currentUser(invalid).getStatus()).isEqualTo(INVALID_TOKEN);
        }
        verifyNoInteractions(users);
    }
    @Test void technicalFailureAndDisabledRecognitionAreDistinct() throws Exception {
        decoder = token -> {throw new JwtException("Key service unavailable");};
        assertThat(currentUser("token").getStatus()).isEqualTo(VERIFICATION_UNAVAILABLE);
        assertThat(currentUser(new MockHttpServletRequest("GET", "/backend/api/v1/current-user"), null).getStatus()).isEqualTo(DISABLED);
        verifyNoInteractions(users);
    }
}
