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
        if (identity.getIdentityInApplications() != null) {
            identity.getIdentityInApplications().forEach(app -> app.setIdentity(identity));
        }
        return identityRepository.save(identity);
    }

    @Transactional
    public IdentityEntity updateIdentity(Long id, IdentityEntity updatedIdentity) {
        IdentityEntity existing = identityRepository.findById(id)
                .orElseThrow(() -> new ThereaboutException(HttpStatusCode.valueOf(404), "Identity with id %d not found".formatted(id)));

        existing.setShortName(updatedIdentity.getShortName());
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

            existingApps.removeIf(app -> app.getId() != null && !updatedIds.contains(app.getId()));

            for (IdentityInApplicationEntity app : updatedApps) {
                if (app.getId() == null || app.getId() == 0) {
                    app.setIdentity(existing);
                    existingApps.add(app);
                }
            }
        }

        return identityRepository.save(existing);
    }

    @Transactional
    public void deleteIdentity(Long id) {
        if (!identityRepository.existsById(id)) {
            throw new ThereaboutException(HttpStatusCode.valueOf(404), "Identity with id %d not found".formatted(id));
        }
        identityRepository.deleteById(id);
    }
}
