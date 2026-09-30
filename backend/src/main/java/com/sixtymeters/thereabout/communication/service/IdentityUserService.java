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
    private final com.sixtymeters.thereabout.client.data.UserPreferencesRepository preferences;

    @Transactional
    public IdentityEntity createUser(Long id, String email, UserRole role) {
        if (role == null) throw new ThereaboutException(HttpStatus.BAD_REQUEST, "Role is required.");
        String normalized = CloudflareEmail.normalize(email);
        // Serializes double clicks, different emails for one person, and generic edits.
        IdentityEntity identity = identities.findForUpdateById(id)
                .orElseThrow(() -> new ThereaboutException(HttpStatus.NOT_FOUND, "Identity not found."));
        if (identity.isGroup()) throw new ThereaboutException(HttpStatus.BAD_REQUEST, "Groups cannot become users.");
        var current = identity.getIdentityInApplications().stream()
                .filter(app -> app.getApplication() == CommunicationApplication.CLOUDFLARE).findFirst();
        if (current.isPresent()) {
            if (identity.isUser() && current.get().getIdentifier().equals(normalized) && identity.getRole() == role) return identity;
            throw new ThereaboutException(HttpStatus.CONFLICT, "This person already has a Cloudflare email.");
        }
        if (applications.findByApplicationAndIdentifier(CommunicationApplication.CLOUDFLARE, normalized).isPresent()) {
            throw new ThereaboutException(HttpStatus.CONFLICT, "This Cloudflare email is already assigned to another person.");
        }
        identity.setRole(role);
        identity.getIdentityInApplications().add(IdentityInApplicationEntity.builder()
                .identity(identity).application(CommunicationApplication.CLOUDFLARE).identifier(normalized).build());
        // The existing unique index arbitrates concurrent claims by different people.
        // An exception exits this transaction and rolls back both the flag and link.
        identity = identities.saveAndFlush(identity);
        if (!preferences.existsById(id)) {
            var value = new com.sixtymeters.thereabout.client.data.UserPreferencesEntity();
            value.setUserId(id); value.setWeightGoalKg(new java.math.BigDecimal("75.0"));
            value.setWeightGoalStartedOn(java.time.LocalDate.now(java.time.ZoneId.of("Europe/Zurich")));
            preferences.save(value);
        }
        return identity;
    }
    @Transactional
    public IdentityEntity changeRole(Long id, UserRole role) {
        if (role == null) throw new ThereaboutException(HttpStatus.BAD_REQUEST, "Role is required.");
        // Lock the complete admin set before the target, so concurrent demotions cannot remove all admins.
        var admins = identities.lockAdmins();
        var identity = identities.findForUpdateById(id)
                .orElseThrow(() -> new ThereaboutException(HttpStatus.NOT_FOUND, "Identity not found."));
        if (!identity.isUser() || identity.isGroup()) throw new ThereaboutException(HttpStatus.BAD_REQUEST, "Only users have a role.");
        if (identity.isAdmin() && role != UserRole.ADMIN && admins.size() <= 1)
            throw new ThereaboutException(HttpStatus.CONFLICT, "The last administrator cannot be demoted.");
        identity.setRole(role);
        return identities.saveAndFlush(identity);
    }
}
