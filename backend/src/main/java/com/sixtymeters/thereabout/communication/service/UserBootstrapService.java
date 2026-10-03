package com.sixtymeters.thereabout.communication.service;

import com.sixtymeters.thereabout.client.data.ConfigurationRepository;
import com.sixtymeters.thereabout.communication.data.IdentityEntity;
import com.sixtymeters.thereabout.communication.data.IdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** An installation without users makes its first verified Cloudflare login the administrator. */
@Service
@RequiredArgsConstructor
public class UserBootstrapService {
    static final String ADMIN_NAME = "Admin";

    private final ConfigurationRepository configuration;
    private final IdentityRepository identities;
    private final IdentityUserService users;

    /**
     * Empty when another login already claimed the bootstrap or users exist. A new transaction starts its
     * snapshot at the claim, so MariaDB's snapshot isolation does not reject waiting for the winner.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<IdentityEntity> createFirstAdmin(String email) {
        String claim = UUID.randomUUID().toString();
        configuration.insertUserBootstrapIfAbsent(claim);
        if (!claim.equals(configuration.lockUserBootstrap()) || identities.anyUserExists()) return Optional.empty();
        IdentityEntity admin = identities.save(IdentityEntity.builder().firstName(ADMIN_NAME).role(com.sixtymeters.thereabout.communication.data.UserRole.ADMIN).build());
        return Optional.of(users.createUser(admin.getId(), email, com.sixtymeters.thereabout.communication.data.UserRole.ADMIN));
    }
}
