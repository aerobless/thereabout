package com.sixtymeters.thereabout.access;

import com.sixtymeters.thereabout.communication.data.IdentityRepository;
import com.sixtymeters.thereabout.communication.data.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** The authenticated actor must be an administrator on every request, including current-user. */
public class ImpersonationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Thereabout-Impersonate-User";
    private final IdentityRepository identities;
    public ImpersonationFilter(IdentityRepository identities) { this.identities = identities; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String target = request.getHeader(HEADER);
        if (target != null) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof AccessPrincipal actor) || actor.role() != UserRole.ADMIN) {
                response.sendError(403); return;
            }
            long id;
            try { id = Long.parseLong(target); if (id <= 0) throw new NumberFormatException(); }
            catch (NumberFormatException invalid) { response.sendError(400); return; }
            var user = identities.findById(id).filter(i -> i.isUser() && !i.isGroup());
            if (user.isEmpty()) { response.sendError(404); return; }
            var person = user.get();
            AccessPrincipal.authenticate(new AccessPrincipal(actor.email(), person.getId(), person.getFirstName(),
                    actor.local(), person.getRole(), actor), AccessPrincipal.roles(true, person.isAdmin()));
        }
        chain.doFilter(request, response);
    }
}
