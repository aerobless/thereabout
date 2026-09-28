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
        if (authentication != null && authentication.getPrincipal() instanceof AccessPrincipal principal && !principal.local()) {
            if (principal.email() == null) return result(INVALID_TOKEN);
            if (principal.identityId() == null) return result(UNLINKED);
            boolean admin = authentication.getAuthorities().stream().anyMatch(role -> AccessPrincipal.ADMIN.equals(role.getAuthority()));
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(GenCurrentUser.builder().status(RESOLVED)
                    .identityId(principal.identityId()).displayName(principal.displayName()).isAdmin(admin).build());
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
