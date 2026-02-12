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
