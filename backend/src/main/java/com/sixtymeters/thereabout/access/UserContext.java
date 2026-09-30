package com.sixtymeters.thereabout.access;

import com.sixtymeters.thereabout.communication.data.IdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserContext {
    private final IdentityRepository identities;

    public UserId current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AccessPrincipal principal) || principal.identityId() == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A personal data user is required.");
        return new UserId(principal.identityId());
    }

    /** Shared ingestion and MCP deliberately retain user 1, independent of browser impersonation. */
    public UserId integration() { return require(1); }

    public UserId require(long id) {
        identities.findById(id).filter(i -> i.isUser() && !i.isGroup())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        return new UserId(id);
    }

    public Long actorId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AccessPrincipal principal)) return null;
        return principal.actor() == null ? principal.identityId() : principal.actor().identityId();
    }
}
