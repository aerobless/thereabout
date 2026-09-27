package com.sixtymeters.thereabout.access;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sixtymeters.thereabout.communication.data.*;
import com.sixtymeters.thereabout.generated.model.GenCurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.*;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.sixtymeters.thereabout.generated.model.GenCurrentUser.StatusEnum.*;

class CurrentUserTest {
    static final String ISSUER = "https://team.cloudflareaccess.com";
    final com.nimbusds.jose.jwk.RSAKey key = new RSAKeyGenerator(2048).generate();
    final MockHttpServletRequest request = new MockHttpServletRequest();
    final IdentityInApplicationRepository applications = mock(IdentityInApplicationRepository.class);
    final ObjectProvider<JwtDecoder> provider = mock(ObjectProvider.class);
    final CurrentUserController controller = new CurrentUserController(provider, request, applications);
    CurrentUserTest() throws Exception {}

    @BeforeEach void setup() throws Exception {
        var decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        decoder.setJwtValidator(CloudflareAccessTokens.validators(ISSUER, "app"));
        when(provider.getIfAvailable()).thenReturn(decoder);
        when(applications.findByApplicationAndIdentifier(any(), any())).thenReturn(Optional.empty());
    }
    String token(String issuer, String audience, Instant expiry, String email, boolean forged) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience).issueTime(Date.from(Instant.now().minusSeconds(600)))
                .claim("email", email);
        if (expiry != null) claims.expirationTime(Date.from(expiry));
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner(forged ? new RSAKeyGenerator(2048).generate() : key));
        return jwt.serialize();
    }
    GenCurrentUser result(String token) {
        request.removeHeader("Cf-Access-Jwt-Assertion");
        if (token != null) request.addHeader("Cf-Access-Jwt-Assertion", token);
        var response = controller.getCurrentUser();
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        var body = response.getBody();
        if (body.getStatus() != RESOLVED) {
            assertThat(body.getIdentityId()).isNull();
            assertThat(body.getDisplayName()).isNull();
        }
        return body;
    }
    @Test void knownEmailUsesStablePersonIdAndShortName() throws Exception {
        var identity = IdentityEntity.builder().id(42L).shortName("Heidi").isUser(true).build();
        when(applications.findByApplicationAndIdentifier(CommunicationApplication.CLOUDFLARE, "heidi@example.test"))
                .thenReturn(Optional.of(IdentityInApplicationEntity.builder().identity(identity).build()));
        var user = result(token(ISSUER, "app", Instant.now().plusSeconds(600), " Heidi@Example.test ", false));
        assertThat(user.getStatus()).isEqualTo(RESOLVED);
        assertThat(user.getIdentityId()).isEqualTo(42L);
        assertThat(user.getDisplayName()).isEqualTo("Heidi");
    }
    @Test void validUnknownLoginIsUnlinked() throws Exception {
        assertThat(result(token(ISSUER, "app", Instant.now().plusSeconds(600), "unknown@example.test", false)).getStatus()).isEqualTo(UNLINKED);
    }
    @Test void plainEmailHeaderDoesNotAuthenticate() {
        request.addHeader("Cf-Access-Authenticated-User-Email", "heidi@example.test");
        assertThat(result(null).getStatus()).isEqualTo(MISSING_TOKEN);
        verifyNoInteractions(applications);
    }
    @Test void invalidSignatureIssuerAudienceExpiryOrEmailNeverResolve() throws Exception {
        Instant future = Instant.now().plusSeconds(600);
        for (String invalid : new String[]{"malformed", token(ISSUER, "app", future, "heidi@example.test", true),
                token("https://wrong.example", "app", future, "heidi@example.test", false),
                token(ISSUER, "wrong", future, "heidi@example.test", false),
                token(ISSUER, "app", Instant.now().minusSeconds(300), "heidi@example.test", false),
                token(ISSUER, "app", null, "heidi@example.test", false),
                token(ISSUER, "app", future, null, false)}) {
            assertThat(result(invalid).getStatus()).isEqualTo(INVALID_TOKEN);
        }
        verifyNoInteractions(applications);
    }
    @Test void technicalFailureAndDisabledRecognitionAreDistinct() {
        when(provider.getIfAvailable()).thenReturn(token -> {throw new JwtException("Key service unavailable");});
        assertThat(result("token").getStatus()).isEqualTo(VERIFICATION_UNAVAILABLE);
        when(provider.getIfAvailable()).thenReturn(null);
        assertThat(result("token").getStatus()).isEqualTo(DISABLED);
        verifyNoInteractions(applications);
    }
}
