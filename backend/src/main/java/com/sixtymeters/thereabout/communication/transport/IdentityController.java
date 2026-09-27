package com.sixtymeters.thereabout.communication.transport;

import com.sixtymeters.thereabout.communication.data.IdentityEntity;
import com.sixtymeters.thereabout.communication.service.IdentityService;
import com.sixtymeters.thereabout.communication.transport.mapper.IdentityMapper;
import com.sixtymeters.thereabout.generated.api.IdentityApi;
import com.sixtymeters.thereabout.generated.model.GenIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
public class IdentityController implements IdentityApi {

    private static final IdentityMapper IDENTITY_MAPPER = IdentityMapper.INSTANCE;
    private final IdentityService identityService;

    private final com.sixtymeters.thereabout.communication.service.IdentityUserService identityUserService;

    @Override
    public ResponseEntity<GenIdentity> createIdentityUser(BigDecimal id,
            com.sixtymeters.thereabout.generated.model.GenCreateUserRequest request) {
        try {
            return ResponseEntity.ok(IDENTITY_MAPPER.mapToGenIdentity(identityUserService.createUser(id.longValueExact(), request.getEmail())));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // The transactional service has already rolled back. Do not expose SQL or identifiers.
            throw new com.sixtymeters.thereabout.config.ThereaboutException(org.springframework.http.HttpStatus.CONFLICT,
                    "This Cloudflare email is already assigned to another person.");
        }
    }

    @Override
    public ResponseEntity<List<GenIdentity>> getIdentities() {
        List<GenIdentity> identities = identityService.getAllIdentities().stream()
                .map(IDENTITY_MAPPER::mapToGenIdentity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(identities);
    }

    @Override
    public ResponseEntity<GenIdentity> createIdentity(GenIdentity genIdentity) {
        IdentityEntity entity = IDENTITY_MAPPER.mapToIdentityEntity(genIdentity);
        IdentityEntity saved = identityService.createIdentity(entity);
        return ResponseEntity.ok(IDENTITY_MAPPER.mapToGenIdentity(saved));
    }

    @Override
    public ResponseEntity<GenIdentity> updateIdentity(BigDecimal id, GenIdentity genIdentity) {
        IdentityEntity entity = IDENTITY_MAPPER.mapToIdentityEntity(genIdentity);
        IdentityEntity updated = identityService.updateIdentity(id.longValue(), entity);
        return ResponseEntity.ok(IDENTITY_MAPPER.mapToGenIdentity(updated));
    }

    @Override
    public ResponseEntity<Void> deleteIdentity(BigDecimal id) {
        identityService.deleteIdentity(id.longValue());
        return ResponseEntity.noContent().build();
    }
}
