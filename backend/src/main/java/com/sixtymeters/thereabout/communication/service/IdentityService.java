package com.sixtymeters.thereabout.communication.service;

import com.sixtymeters.thereabout.communication.data.IdentityEntity;
import com.sixtymeters.thereabout.communication.data.IdentityRepository;
import com.sixtymeters.thereabout.config.ThereaboutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        identity.getIdentityInApplications().forEach(app -> app.setIdentity(identity));
        return identityRepository.save(identity);
    }

    @Transactional
    public IdentityEntity updateIdentity(Long id, IdentityEntity updatedIdentity) {
        IdentityEntity existing = identityRepository.findById(id)
                .orElseThrow(() -> new ThereaboutException(HttpStatusCode.valueOf(404), "Identity with id %d not found".formatted(id)));

        existing.setShortName(updatedIdentity.getShortName());
        existing.setGroup(updatedIdentity.isGroup());
        existing.setRelationship(updatedIdentity.getRelationship());

        // Sync the identity_in_application list
        existing.getIdentityInApplications().clear();
        updatedIdentity.getIdentityInApplications().forEach(app -> {
            app.setIdentity(existing);
            existing.getIdentityInApplications().add(app);
        });

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
