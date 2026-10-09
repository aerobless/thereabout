package com.sixtymeters.thereabout.communication.transport;

import com.sixtymeters.thereabout.communication.data.CommunicationApplication;
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
    private final com.sixtymeters.thereabout.communication.service.IdentityOperations operations;

    @Override
    public ResponseEntity<List<GenIdentityInApplication>> getIdentityInApplicationsByApplication(String application) {
        CommunicationApplication app = IdentityMapper.INSTANCE.displayNameToEnum(application);
        List<GenIdentityInApplication> result = identityInApplicationService.getByApplication(app).stream()
                .map(IDENTITY_MAPPER::mapToGenIdentityInApplication)
                .toList();
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<List<GenIdentityInApplication>> getUnlinkedIdentityInApplications() {
        List<GenIdentityInApplication> unlinked = identityInApplicationService.getUnlinkedAppIdentities().stream()
                .map(IDENTITY_MAPPER::mapToGenIdentityInApplication)
                .toList();
        return ResponseEntity.ok(unlinked);
    }

    @Override
    public ResponseEntity<GenIdentityInApplication> linkIdentityInApplication(BigDecimal id, BigDecimal identityId, java.util.Optional<Long> identityVersion, java.util.Optional<Long> version, java.util.Optional<String> requestKey) {
        return ResponseEntity.ok(operations.link(new com.sixtymeters.thereabout.generated.model.GenIdentityLinkInput()
            .id(id.longValueExact()).identityId(identityId.longValueExact()).identityVersion(identityVersion.orElse(null)).version(version.orElse(null)).requestKey(requestKey.orElse(null))));
    }

    @Override
    public ResponseEntity<GenIdentityInApplication> unlinkIdentityInApplication(BigDecimal id, java.util.Optional<Long> version, java.util.Optional<String> requestKey) {
        return ResponseEntity.ok(operations.unlink(new com.sixtymeters.thereabout.generated.model.GenIdentityVersionedInput()
            .id(id.longValueExact()).version(version.orElse(null)).requestKey(requestKey.orElse(null))));
    }
}
