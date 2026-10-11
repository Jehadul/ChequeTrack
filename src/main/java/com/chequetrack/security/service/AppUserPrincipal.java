package com.chequetrack.security.service;

import com.chequetrack.security.domain.AppUser;
import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * The logged-in user as Spring Security sees it, for both web sessions and API tokens.
 * A snapshot taken at login; it is stored in the HTTP session, so it must stay serializable.
 */
@Getter
public class AppUserPrincipal implements UserDetails, CredentialsContainer {

    private final Long id;
    private final String username;
    private final String fullName;
    private String password;
    private final boolean enabled;
    private final boolean locked;
    /** TRUE when the user must choose a new password before doing anything else. */
    private final boolean passwordChangeRequired;
    /** Filled from roles and permissions in M2. */
    private final List<GrantedAuthority> authorities;

    public AppUserPrincipal(AppUser user, Instant now, int passwordExpiryDays) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.fullName = user.getFullName();
        this.password = user.getPasswordHash();
        this.enabled = user.isEnabled();
        this.locked = user.isLockedAt(now);
        boolean expired = passwordExpiryDays > 0
                && user.getPasswordChangedAt().plusSeconds(passwordExpiryDays * 86_400L).isBefore(now);
        this.passwordChangeRequired = user.isMustChangePassword() || expired;
        this.authorities = List.of();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    /** Never hold the hash in the session longer than needed. */
    @Override
    public void eraseCredentials() {
        this.password = null;
    }
}
