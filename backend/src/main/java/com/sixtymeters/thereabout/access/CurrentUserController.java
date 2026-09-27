package com.sixtymeters.thereabout.access;

import com.sixtymeters.thereabout.communication.data.*;
import com.sixtymeters.thereabout.communication.service.CloudflareEmail;
import com.sixtymeters.thereabout.generated.api.CurrentUserApi;
import com.sixtymeters.thereabout.generated.model.GenCurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import static com.sixtymeters.thereabout.generated.model.GenCurrentUser.StatusEnum.*;

@RestController
public class CurrentUserController implements CurrentUserApi {
    private final ObjectProvider<JwtDecoder> decoders;
    private final HttpServletRequest request;
    private final IdentityInApplicationRepository applications;

    public CurrentUserController(@Qualifier("userAccessTokenDecoder") ObjectProvider<JwtDecoder> decoders,
            HttpServletRequest request, IdentityInApplicationRepository applications) {
        this.decoders = decoders;
        this.request = request;
        this.applications = applications;
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<GenCurrentUser> getCurrentUser() {
        JwtDecoder decoder = decoders.getIfAvailable();
        if (decoder == null) return result(DISABLED);
        String token = request.getHeader("Cf-Access-Jwt-Assertion");
        if (token == null || token.isBlank()) return result(MISSING_TOKEN);
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (BadJwtException | IllegalArgumentException e) {
            return result(INVALID_TOKEN);
        } catch (JwtException e) {
            // Key retrieval/verification infrastructure failed. Never expose exception text or tokens.
            return result(VERIFICATION_UNAVAILABLE);
        }
        String email;
        try {
            email = CloudflareEmail.normalize(jwt.getClaimAsString("email"));
        } catch (RuntimeException e) {
            return result(INVALID_TOKEN);
        }
        var mapping = applications.findByApplicationAndIdentifier(CommunicationApplication.CLOUDFLARE, email);
        if (mapping.isEmpty() || mapping.get().getIdentity() == null) return result(UNLINKED);
        var identity = mapping.get().getIdentity();
        if (!identity.isUser() || identity.isGroup()) return result(UNLINKED);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(GenCurrentUser.builder()
                .status(RESOLVED).identityId(identity.getId()).displayName(identity.getShortName()).build());
    }

    private ResponseEntity<GenCurrentUser> result(GenCurrentUser.StatusEnum status) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(GenCurrentUser.builder().status(status).build());
    }
}
