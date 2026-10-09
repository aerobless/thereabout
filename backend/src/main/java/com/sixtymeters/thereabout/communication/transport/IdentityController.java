package com.sixtymeters.thereabout.communication.transport;

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
    private final com.sixtymeters.thereabout.communication.service.IdentityOperations operations;
    private final com.sixtymeters.thereabout.communication.service.GroupMembershipService memberships;

    @Override
    public ResponseEntity<com.sixtymeters.thereabout.generated.model.GenGroupMembers> getGroupMembers(Long id) {
        return ResponseEntity.ok(memberships.get(id));
    }

    @Override
    public ResponseEntity<com.sixtymeters.thereabout.generated.model.GenGroupMembers> saveGroupMembers(Long id,
            com.sixtymeters.thereabout.generated.model.GenGroupMembers input) {
        return ResponseEntity.ok(operations.saveMembers(new com.sixtymeters.thereabout.generated.model.GenIdentityMembershipInput()
            .id(id).version(input.getVersion()).requestKey(input.getRequestKey()).userIds(input.getUserIds())));
    }

    private final com.sixtymeters.thereabout.communication.service.IdentityUserService identityUserService;

    @Override
    public ResponseEntity<GenIdentity> createIdentityUser(BigDecimal id,
            com.sixtymeters.thereabout.generated.model.GenCreateUserRequest request) {
        try {
            return ResponseEntity.ok(IDENTITY_MAPPER.mapToGenIdentity(identityUserService.createUser(id.longValueExact(), request.getEmail(), com.sixtymeters.thereabout.communication.data.UserRole.valueOf(request.getRole().name()))));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // The transactional service has already rolled back. Do not expose SQL or identifiers.
            throw new com.sixtymeters.thereabout.config.ThereaboutException(org.springframework.http.HttpStatus.CONFLICT,
                    "This Cloudflare email is already assigned to another person.");
        }
    }

    @Override
    public ResponseEntity<GenIdentity> updateIdentityUserRole(BigDecimal id,
            com.sixtymeters.thereabout.generated.model.GenUpdateUserRoleRequest request) {
        return ResponseEntity.ok(IDENTITY_MAPPER.mapToGenIdentity(identityUserService.changeRole(id.longValueExact(),
                com.sixtymeters.thereabout.communication.data.UserRole.valueOf(request.getRole().name()))));
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
        return ResponseEntity.ok(operations.create(genIdentity));
    }

    @Override
    public ResponseEntity<GenIdentity> updateIdentity(BigDecimal id, GenIdentity genIdentity) {
        return ResponseEntity.ok(operations.update(id.longValueExact(), genIdentity));
    }

    @Override
    public ResponseEntity<Void> deleteIdentity(BigDecimal id, java.util.Optional<Long> version, java.util.Optional<String> requestKey) {
        operations.delete(new com.sixtymeters.thereabout.generated.model.GenIdentityVersionedInput().id(id.longValueExact()).version(version.orElse(null)).requestKey(requestKey.orElse(null)));
        return ResponseEntity.noContent().build();
    }
}
