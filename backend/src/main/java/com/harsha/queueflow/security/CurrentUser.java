package com.harsha.queueflow.security;

import com.harsha.queueflow.enums.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.harsha.queueflow.exception.ForbiddenOperationException;

/**
 * Resolves the currently authenticated QueueFlow principal.
 *
 * <p>This is the single source of truth for "who is calling". Controllers and
 * services never accept a user id from the client, which is what makes token
 * ownership enforceable.</p>
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static QueueFlowPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof QueueFlowPrincipal principal)) {
            throw new ForbiddenOperationException("Authentication required to access this resource");
        }
        return principal;
    }

    public static QueueFlowPrincipal require(Long id) {
        QueueFlowPrincipal principal = require();
        if (principal.getId().equals(id)) {
            return principal;
        }
        throw new ForbiddenOperationException("You may only access your own resources");
    }

    public static Long id() {
        return require().getId();
    }

    public static Role role() {
        return require().getRole();
    }

    public static boolean hasRole(Role role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role.name()));
    }
}
