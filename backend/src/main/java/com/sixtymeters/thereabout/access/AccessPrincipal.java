package com.sixtymeters.thereabout.access;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import java.util.Collection;
import java.util.List;

/**
 * Who a request acts for. A verified Cloudflare login without a Thereabout user (an unknown email or a
 * service token without email) is authenticated but has no role, so it can only report its own status.
 */
public record AccessPrincipal(String email, Long identityId, String displayName, boolean local) {
    public static final String USER = "ROLE_USER";
    public static final String ADMIN = "ROLE_ADMIN";

    static final AccessPrincipal LOCAL = new AccessPrincipal(null, null, null, true);

    static List<GrantedAuthority> roles(boolean user, boolean admin) {
        if (!user) return List.of();
        return admin ? AuthorityUtils.createAuthorityList(USER, ADMIN) : AuthorityUtils.createAuthorityList(USER);
    }

    static void authenticate(Object principal, Collection<? extends GrantedAuthority> authorities) {
        var strategy = SecurityContextHolder.getContextHolderStrategy();
        var context = strategy.createEmptyContext();
        context.setAuthentication(new PreAuthenticatedAuthenticationToken(principal, null, authorities));
        strategy.setContext(context);
    }
}
