package com.sixtymeters.thereabout.communication.service;

import com.sixtymeters.thereabout.communication.data.*;
import com.sixtymeters.thereabout.config.ThereaboutException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdentityUserService {
    private final IdentityRepository identities;
    private final IdentityInApplicationRepository applications;

    @Transactional
    public IdentityEntity createUser(Long id, String email) {
        String normalized = CloudflareEmail.normalize(email);
        // Serializes double clicks, different emails for one person, and generic edits.
        IdentityEntity identity = identities.findForUpdateById(id)
                .orElseThrow(() -> new ThereaboutException(HttpStatus.NOT_FOUND, "Identity not found."));
        if (identity.isGroup()) throw new ThereaboutException(HttpStatus.BAD_REQUEST, "Groups cannot become users.");
        var current = identity.getIdentityInApplications().stream()
                .filter(app -> app.getApplication() == CommunicationApplication.CLOUDFLARE).findFirst();
        if (current.isPresent()) {
            if (identity.isUser() && current.get().getIdentifier().equals(normalized)) return identity;
            throw new ThereaboutException(HttpStatus.CONFLICT, "This person already has a Cloudflare email.");
        }
        if (applications.findByApplicationAndIdentifier(CommunicationApplication.CLOUDFLARE, normalized).isPresent()) {
            throw new ThereaboutException(HttpStatus.CONFLICT, "This Cloudflare email is already assigned to another person.");
        }
        identity.setUser(true);
        identity.getIdentityInApplications().add(IdentityInApplicationEntity.builder()
                .identity(identity).application(CommunicationApplication.CLOUDFLARE).identifier(normalized).build());
        // The existing unique index arbitrates concurrent claims by different people.
        // An exception exits this transaction and rolls back both the flag and link.
        return identities.saveAndFlush(identity);
    }
}
