package com.sixtymeters.thereabout.users;

import com.sixtymeters.thereabout.communication.data.*;
import com.sixtymeters.thereabout.communication.service.IdentityUserService;
import com.sixtymeters.thereabout.generated.model.GenCurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
    private final CloudflareUserAccess access;
    private final IdentityInApplicationRepository applications;

    @Transactional(readOnly = true)
    public GenCurrentUser currentUser(HttpServletRequest request) {
        var verified = access.resolve(request);
        if (verified.state() != CloudflareUserAccess.State.VERIFIED) {
            return new GenCurrentUser().status(GenCurrentUser.StatusEnum.valueOf(verified.state().name()));
        }
        var identity = applications.findByApplicationAndIdentifier(CommunicationApplication.CLOUDFLARE,
                        IdentityUserService.normalizeEmail(verified.email()))
                .map(IdentityInApplicationEntity::getIdentity)
                .filter(person -> person.isUser() && !person.isGroup());
        return identity.map(person -> new GenCurrentUser().status(GenCurrentUser.StatusEnum.AUTHENTICATED)
                        .identityId(BigDecimal.valueOf(person.getId())).displayName(person.getShortName()))
                .orElseGet(() -> new GenCurrentUser().status(GenCurrentUser.StatusEnum.UNASSIGNED));
    }
}
