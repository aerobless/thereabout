package com.sixtymeters.thereabout.communication.service;

import com.sixtymeters.thereabout.communication.data.*;
import com.sixtymeters.thereabout.config.ThereaboutException;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class IdentityUserService {
    private final IdentityRepository identities;
    private final IdentityInApplicationRepository applications;
    private final Validator validator;

    private record EmailInput(@NotBlank @Email @Size(max = 255) String email) {}

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public IdentityEntity createUser(long id, String rawEmail) {
        String email = normalizeEmail(rawEmail);
        if (!validator.validate(new EmailInput(email)).isEmpty()) {
            throw new ThereaboutException(HttpStatus.BAD_REQUEST, "Enter a valid Cloudflare email.");
        }
        IdentityEntity identity = identities.findForUpdate(id)
                .orElseThrow(() -> new ThereaboutException(HttpStatus.NOT_FOUND, "Identity not found."));
        if (identity.isGroup()) {
            throw new ThereaboutException(HttpStatus.BAD_REQUEST, "Groups cannot become users.");
        }
        var existing = identity.getIdentityInApplications().stream()
                .filter(a -> a.getApplication() == CommunicationApplication.CLOUDFLARE).findFirst();
        if (existing.isPresent()) {
            if (!email.equals(existing.get().getIdentifier())) {
                throw new ThereaboutException(HttpStatus.CONFLICT, "This user already has a different Cloudflare email.");
            }
            identity.setUser(true);
            return identities.saveAndFlush(identity);
        }
        if (applications.findByApplicationAndIdentifier(CommunicationApplication.CLOUDFLARE, email).isPresent()) {
            throw new ThereaboutException(HttpStatus.CONFLICT, "This Cloudflare email is already assigned.");
        }
        identity.getIdentityInApplications().add(IdentityInApplicationEntity.builder()
                .identity(identity).application(CommunicationApplication.CLOUDFLARE).identifier(email).build());
        identity.setUser(true);
        // The database unique index also arbitrates concurrent claims by different people.
        return identities.saveAndFlush(identity);
    }
}
