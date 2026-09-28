package com.sixtymeters.thereabout.access;

import com.sixtymeters.thereabout.communication.data.CommunicationApplication;
import com.sixtymeters.thereabout.communication.data.IdentityEntity;
import com.sixtymeters.thereabout.communication.data.IdentityInApplicationRepository;
import com.sixtymeters.thereabout.communication.data.IdentityRepository;
import com.sixtymeters.thereabout.communication.service.UserBootstrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Maps a verified, normalized Cloudflare email to its Thereabout user. */
@Service
@RequiredArgsConstructor
public class CloudflareUsers {
    private final IdentityInApplicationRepository applications;
    private final IdentityRepository identities;
    private final UserBootstrapService bootstrap;

    public record User(long identityId, String displayName, boolean admin) {}

    @Transactional
    public Optional<User> resolve(String email) {
        var mapping = applications.findByApplicationAndIdentifier(CommunicationApplication.CLOUDFLARE, email);
        Optional<IdentityEntity> identity = mapping.isPresent()
                ? Optional.ofNullable(mapping.get().getIdentity())
                : identities.anyUserExists() ? Optional.empty() : bootstrap.createFirstAdmin(email);
        return identity.filter(person -> person.isUser() && !person.isGroup())
                .map(person -> new User(person.getId(), person.getShortName(), person.isAdmin()));
    }
}
