package com.sixtymeters.thereabout.communication.transport;

import com.sixtymeters.thereabout.communication.data.IdentityInApplicationEntity;
import com.sixtymeters.thereabout.communication.service.IdentityInApplicationService;
import com.sixtymeters.thereabout.communication.transport.mapper.IdentityMapper;
import com.sixtymeters.thereabout.generated.api.IdentityInApplicationApi;
import com.sixtymeters.thereabout.generated.model.GenIdentityInApplication;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class IdentityInApplicationController implements IdentityInApplicationApi {

    private static final IdentityMapper IDENTITY_MAPPER = IdentityMapper.INSTANCE;
    private final IdentityInApplicationService identityInApplicationService;

    @Override
    public ResponseEntity<List<GenIdentityInApplication>> getUnlinkedIdentityInApplications() {
        List<GenIdentityInApplication> unlinked = identityInApplicationService.getUnlinkedAppIdentities().stream()
                .map(IDENTITY_MAPPER::mapToGenIdentityInApplication)
                .toList();
        return ResponseEntity.ok(unlinked);
    }

    @Override
    public ResponseEntity<GenIdentityInApplication> linkIdentityInApplication(BigDecimal id, BigDecimal identityId) {
        IdentityInApplicationEntity linked = identityInApplicationService.linkAppIdentity(id.longValue(), identityId.longValue());
        return ResponseEntity.ok(IDENTITY_MAPPER.mapToGenIdentityInApplication(linked));
    }

    @Override
    public ResponseEntity<GenIdentityInApplication> unlinkIdentityInApplication(BigDecimal id) {
        IdentityInApplicationEntity unlinked = identityInApplicationService.unlinkAppIdentity(id.longValue());
        return ResponseEntity.ok(IDENTITY_MAPPER.mapToGenIdentityInApplication(unlinked));
    }
}
