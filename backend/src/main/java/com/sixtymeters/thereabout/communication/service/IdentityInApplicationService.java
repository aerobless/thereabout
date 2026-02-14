package com.sixtymeters.thereabout.communication.service;

import com.sixtymeters.thereabout.communication.data.IdentityEntity;
import com.sixtymeters.thereabout.communication.data.IdentityInApplicationEntity;
import com.sixtymeters.thereabout.communication.data.IdentityInApplicationRepository;
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
public class IdentityInApplicationService {

    private final IdentityInApplicationRepository identityInApplicationRepository;
    private final IdentityRepository identityRepository;

    public List<IdentityInApplicationEntity> getUnlinkedAppIdentities() {
        return identityInApplicationRepository.findByIdentityIsNull();
    }

    @Transactional
    public IdentityInApplicationEntity linkAppIdentity(Long appIdentityId, Long identityId) {
        IdentityInApplicationEntity appIdentity = identityInApplicationRepository.findById(appIdentityId)
                .orElseThrow(() -> new ThereaboutException(HttpStatusCode.valueOf(404),
                        "Application identity with id %d not found".formatted(appIdentityId)));

        IdentityEntity identity = identityRepository.findById(identityId)
                .orElseThrow(() -> new ThereaboutException(HttpStatusCode.valueOf(404),
                        "Identity with id %d not found".formatted(identityId)));

        appIdentity.setIdentity(identity);
        return identityInApplicationRepository.save(appIdentity);
    }

    @Transactional
    public IdentityInApplicationEntity unlinkAppIdentity(Long appIdentityId) {
        IdentityInApplicationEntity appIdentity = identityInApplicationRepository.findById(appIdentityId)
                .orElseThrow(() -> new ThereaboutException(HttpStatusCode.valueOf(404),
                        "Application identity with id %d not found".formatted(appIdentityId)));

        appIdentity.setIdentity(null);
        return identityInApplicationRepository.save(appIdentity);
    }
}
