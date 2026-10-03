package com.sixtymeters.thereabout.communication.service;

import com.sixtymeters.thereabout.communication.data.IdentityEntity;
import com.sixtymeters.thereabout.communication.data.IdentityInApplicationEntity;
import com.sixtymeters.thereabout.communication.data.IdentityRepository;
import com.sixtymeters.thereabout.config.ThereaboutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityService {

    private final IdentityRepository identityRepository;

    public List<IdentityEntity> getAllIdentities() {
        return identityRepository.findAll();
    }

    @Transactional
    public IdentityEntity createIdentity(IdentityEntity identity) {
        normalizeNames(identity);
        identity.setRole(null);
        if (identity.getIdentityInApplications() != null) {
            if (identity.getIdentityInApplications().stream().anyMatch(IdentityService::isCloudflare)) {
                throw new ThereaboutException(HttpStatusCode.valueOf(400), "Use Create User to assign a Cloudflare email.");
            }
            identity.getIdentityInApplications().forEach(app -> app.setIdentity(identity));
        }
        return identityRepository.save(identity);
    }

    @Transactional
    public IdentityEntity updateIdentity(Long id, IdentityEntity updatedIdentity) {
        IdentityEntity existing = identityRepository.findForUpdateById(id)
                .orElseThrow(() -> new ThereaboutException(HttpStatusCode.valueOf(404), "Identity with id %d not found".formatted(id)));

        if (existing.isUser() && updatedIdentity.isGroup()) {
            throw new ThereaboutException(HttpStatusCode.valueOf(400), "Users cannot become groups.");
        }
        normalizeNames(updatedIdentity);
        existing.setFirstName(updatedIdentity.getFirstName());
        existing.setLastName(updatedIdentity.getLastName());
        existing.setGroup(updatedIdentity.isGroup());
        existing.setRelationship(updatedIdentity.getRelationship());

        // Sync the identity_in_application list (only when provided; null or empty means keep existing)
        // Reuse existing entities when ids match to avoid duplicate key on (application, identifier)
        List<IdentityInApplicationEntity> updatedApps = updatedIdentity.getIdentityInApplications();
        if (updatedApps != null && !updatedApps.isEmpty()) {
            List<IdentityInApplicationEntity> existingApps = existing.getIdentityInApplications();
            Set<Long> updatedIds = updatedApps.stream()
                    .map(IdentityInApplicationEntity::getId)
                    .filter(appId -> appId != null && appId != 0)
                    .collect(Collectors.toSet());

            for (IdentityInApplicationEntity app : updatedApps) {
                if (isCloudflare(app) && existingApps.stream().noneMatch(old -> isCloudflare(old)
                        && java.util.Objects.equals(old.getId(), app.getId())
                        && old.getIdentifier().equals(app.getIdentifier()) && !app.isGroup())) {
                    throw new ThereaboutException(HttpStatusCode.valueOf(400), "Cloudflare identities are managed by Create User.");
                }
            }
            existingApps.removeIf(app -> !isCloudflare(app) && app.getId() != null && !updatedIds.contains(app.getId()));

            for (IdentityInApplicationEntity app : updatedApps) {
                if (app.getId() == null || app.getId() == 0) {
                    app.setIdentity(existing);
                    existingApps.add(app);
                }
            }
        }

        return identityRepository.save(existing);
    }

    private static boolean isCloudflare(IdentityInApplicationEntity app) {
        return app.getApplication() == com.sixtymeters.thereabout.communication.data.CommunicationApplication.CLOUDFLARE;
    }

    private static void normalizeNames(IdentityEntity identity) {
        String first = identity.getFirstName() == null ? "" : identity.getFirstName().strip();
        String last = identity.getLastName() == null ? "" : identity.getLastName().strip();
        if (first.isBlank() || first.length() > 255 || last.length() > 255
                || (identity.isGroup() && !last.isEmpty())) {
            throw new ThereaboutException(HttpStatusCode.valueOf(400),
                    "Enter a first name or group name of at most 255 characters. Groups cannot have a last name.");
        }
        identity.setFirstName(first);
        identity.setLastName(last);
    }

    @Transactional
    public void deleteIdentity(Long id) {
        IdentityEntity existing = identityRepository.findForUpdateById(id)
                .orElseThrow(() -> new ThereaboutException(HttpStatusCode.valueOf(404), "Identity not found."));
        if (existing.isUser()) {
            throw new ThereaboutException(HttpStatusCode.valueOf(400), "User deletion is not supported.");
        }
        identityRepository.deleteById(id);
    }
}
