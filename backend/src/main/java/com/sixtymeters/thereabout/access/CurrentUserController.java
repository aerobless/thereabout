package com.sixtymeters.thereabout.access;

import com.sixtymeters.thereabout.generated.api.CurrentUserApi;
import com.sixtymeters.thereabout.generated.model.GenCurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import static com.sixtymeters.thereabout.generated.model.GenCurrentUser.StatusEnum.*;

/** Reports the login that {@link CloudflareAccessFilter} verified; it never decodes tokens itself. */
@RestController
public class CurrentUserController implements CurrentUserApi {
    private final HttpServletRequest request;

    public CurrentUserController(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public ResponseEntity<GenCurrentUser> getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AccessPrincipal principal) {
            var actor = principal.actor() == null ? principal : principal.actor();
            if (principal.identityId() == null && !principal.local()) return result(principal.email() == null ? INVALID_TOKEN : UNLINKED);
            var user = GenCurrentUser.builder().status(principal.identityId() == null ? DISABLED : RESOLVED)
                    .identityId(principal.identityId()).displayName(principal.displayName())
                    .role(principal.role() == null ? null : com.sixtymeters.thereabout.generated.model.GenUserRole.valueOf(principal.role().name()))
                    .actorIdentityId(actor.identityId()).actorDisplayName(actor.displayName())
                    .actorRole(actor.role() == null ? null : com.sixtymeters.thereabout.generated.model.GenUserRole.valueOf(actor.role().name()))
                    .impersonating(principal.actor() != null).build();
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(user);
        }
        if (!(request.getAttribute(CloudflareAccessFilter.STATUS) instanceof CloudflareAccessFilter.Status status)) return result(DISABLED);
        return result(switch (status) {
            case MISSING_TOKEN -> MISSING_TOKEN;
            case VERIFICATION_UNAVAILABLE -> VERIFICATION_UNAVAILABLE;
            case INVALID_TOKEN, VERIFIED -> INVALID_TOKEN;
        });
    }

    private ResponseEntity<GenCurrentUser> result(GenCurrentUser.StatusEnum status) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(GenCurrentUser.builder().status(status).build());
    }
}
