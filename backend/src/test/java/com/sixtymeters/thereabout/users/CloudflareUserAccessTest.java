package com.sixtymeters.thereabout.users;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import com.sixtymeters.thereabout.communication.data.*;
import com.sixtymeters.thereabout.generated.model.GenCurrentUser;
import com.sixtymeters.thereabout.shared.access.CloudflareTokens;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.*;

class CloudflareUserAccessTest {
    private static final String ISSUER = "https://team.cloudflareaccess.com";
    private static final String ORIGIN = "https://thereabout.example.test";
    private static KeyPair keys;
    private CloudflareUserAccess access;
    @BeforeAll static void keys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); keys = generator.generateKeyPair();
    }
    @BeforeEach void setup() {
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
        decoder.setJwtValidator(CloudflareTokens.validators(ISSUER, "thereabout"));
        access = new CloudflareUserAccess("cloudflare", ORIGIN, false, decoder);
    }
    private String token(String issuer, String audience, String email, Instant expires, KeyPair pair) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience)
                .issueTime(Date.from(Instant.now().minusSeconds(600))).expirationTime(Date.from(expires));
        if (email != null) claims.claim("email", email);
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner(pair.getPrivate())); return jwt.serialize();
    }
    private String valid() throws Exception { return token(ISSUER, "thereabout", "Heidi@Example.test", Instant.now().plusSeconds(300), keys); }
    private MockHttpServletRequest request(String token) {
        var request = new MockHttpServletRequest(); request.setRemoteAddr("172.20.0.4"); request.setServerName("thereabout.example.test");
        if (token != null) request.addHeader("Cf-Access-Jwt-Assertion", token);
        return request;
    }
    @Test void verifiesRealSignaturesAndClaimsBeforeReturningEmail() throws Exception {
        assertThat(access.resolve(request(valid()))).isEqualTo(new CloudflareUserAccess.Result(CloudflareUserAccess.State.VERIFIED, "Heidi@Example.test"));
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        assertThat(access.resolve(request(token(ISSUER,"thereabout","heidi@example.test",Instant.now().plusSeconds(300),generator.generateKeyPair()))).state()).isEqualTo(CloudflareUserAccess.State.INVALID_IDENTITY);
        for (String token : List.of(token("https://other.test", "thereabout", "a@b.test", Instant.now().plusSeconds(300), keys),
                token(ISSUER,"other-app","a@b.test",Instant.now().plusSeconds(300),keys),
                token(ISSUER,"thereabout","a@b.test",Instant.now().minusSeconds(300),keys), "forged")) {
            assertThat(access.resolve(request(token)).state()).isEqualTo(CloudflareUserAccess.State.INVALID_IDENTITY);
        }
    }
    @Test void ignoresUnverifiedEmailHeaderAndDistinguishesUnavailable() {
        var request = request(null); request.addHeader("Cf-Access-Authenticated-User-Email", "heidi@example.test");
        assertThat(access.resolve(request).state()).isEqualTo(CloudflareUserAccess.State.NO_IDENTITY);
        var unavailable = new CloudflareUserAccess("cloudflare", ORIGIN, false, value -> { throw new JwtException("keys unavailable"); });
        assertThat(unavailable.resolve(request("token")).state()).isEqualTo(CloudflareUserAccess.State.UNAVAILABLE);
    }
    @Test void serviceTokensWithoutEmailDoNotBecomePeople() throws Exception {
        assertThat(access.resolve(request(token(ISSUER,"thereabout",null,Instant.now().plusSeconds(300),keys))).state()).isEqualTo(CloudflareUserAccess.State.NO_IDENTITY);
    }
    @Test void permitsInitialMappingWithoutAnExistingUserButRejectsCrossOriginRequests() throws Exception {
        var request = request(valid()); request.addHeader("Origin", ORIGIN);
        assertThatCode(() -> access.requireUserCreationAccess(request)).doesNotThrowAnyException();
        request.removeHeader("Origin"); request.addHeader("Origin", "https://other.test");
        assertThatThrownBy(() -> access.requireUserCreationAccess(request)).hasMessageContaining("403");
        assertThatThrownBy(() -> access.requireUserCreationAccess(request(null))).hasMessageContaining("401");
    }
    @Test void localModeRequiresDevelopmentProfileAndLoopback() {
        var request = request(null); request.setRemoteAddr("127.0.0.1"); request.setServerName("localhost");
        var local = new CloudflareUserAccess("local", "", true, null);
        assertThatCode(() -> local.requireUserCreationAccess(request)).doesNotThrowAnyException();
        var deployed = new CloudflareUserAccess("local", "", false, null);
        assertThatThrownBy(() -> deployed.requireUserCreationAccess(request)).hasMessageContaining("401");
        request.setRemoteAddr("172.20.0.4");
        assertThatThrownBy(() -> local.requireUserCreationAccess(request)).hasMessageContaining("401");
    }
    @Test void resolvesOnlyLinkedActivePeopleAndDoesNotRetainThePreviousUser() throws Exception {
        var apps = mock(IdentityInApplicationRepository.class);
        var person = IdentityEntity.builder().id(5L).shortName("Heidi").isUser(true).build();
        var link = IdentityInApplicationEntity.builder().identity(person).build();
        when(apps.findByApplicationAndIdentifier(CommunicationApplication.CLOUDFLARE,"heidi@example.test")).thenReturn(Optional.of(link));
        var service = new CurrentUserService(access, apps);
        var current = service.currentUser(request(valid()));
        assertThat(current.getStatus()).isEqualTo(GenCurrentUser.StatusEnum.AUTHENTICATED);
        assertThat(current.getDisplayName()).isEqualTo("Heidi");
        assertThat(service.currentUser(request(null)).getIdentityId()).isNull();
        person.setUser(false);
        assertThat(service.currentUser(request(valid())).getStatus()).isEqualTo(GenCurrentUser.StatusEnum.UNASSIGNED);
        person.setUser(true); person.setGroup(true);
        assertThat(service.currentUser(request(valid())).getStatus()).isEqualTo(GenCurrentUser.StatusEnum.UNASSIGNED);
        when(apps.findByApplicationAndIdentifier(any(),anyString())).thenReturn(Optional.empty());
        assertThat(service.currentUser(request(valid())).getStatus()).isEqualTo(GenCurrentUser.StatusEnum.UNASSIGNED);
    }
}
