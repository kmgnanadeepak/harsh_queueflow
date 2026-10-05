package com.harsha.queueflow.security;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.harsha.queueflow.entity.User;
import com.harsha.queueflow.enums.Role;

/**
 * Authenticated QueueFlow principal.
 *
 * <p>Implements {@link UserDetails} so it plugs straight into Spring Security's
 * {@code DaoAuthenticationProvider} and {@code AuthenticationManager} while also
 * exposing the JPA identifier required for ownership checks.</p>
 */
public class QueueFlowPrincipal implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String name;
    private final String email;
    private final String password;
    private final Role role;
    private final boolean active;
    private final String provider;

    public QueueFlowPrincipal(User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.role = user.getRole();
        this.active = user.isActive();
        this.provider = user.getProvider().name();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public String getProvider() {
        return provider;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    public String rolesAsString() {
        return getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(","));
    }
}
