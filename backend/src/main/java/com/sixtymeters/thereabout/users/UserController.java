package com.sixtymeters.thereabout.users;

import com.sixtymeters.thereabout.communication.service.IdentityUserService;
import com.sixtymeters.thereabout.communication.transport.mapper.IdentityMapper;
import com.sixtymeters.thereabout.config.ThereaboutException;
import com.sixtymeters.thereabout.generated.api.UsersApi;
import com.sixtymeters.thereabout.generated.model.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
public class UserController implements UsersApi {
    private final IdentityUserService users;
    private final CurrentUserService currentUsers;
    private final CloudflareUserAccess access;
    private final HttpServletRequest request;

    @Override
    public ResponseEntity<GenIdentity> createUser(BigDecimal id, GenCreateUserRequest body) {
        access.requireUserCreationAccess(request);
        try {
            var identity = users.createUser(id.longValueExact(), body.getEmail());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(IdentityMapper.INSTANCE.mapToGenIdentity(identity));
        } catch (ArithmeticException e) {
            throw new ThereaboutException(HttpStatus.BAD_REQUEST, "Invalid identity ID.");
        } catch (DataIntegrityViolationException e) {
            throw new ThereaboutException(HttpStatus.CONFLICT, "This Cloudflare email is already assigned.");
        }
    }

    @Override
    public ResponseEntity<GenCurrentUser> getCurrentUser() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(currentUsers.currentUser(request));
    }
}
