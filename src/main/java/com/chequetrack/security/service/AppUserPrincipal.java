package com.chequetrack.security.service;

import com.chequetrack.security.domain.AppUser;
import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The logged-in user as Spring Security sees it, for both web sessions and API tokens.
 * A snapshot taken at login and refreshed when the user's access stamp changes.
 * It is stored in the HTTP session, so every field must be serializable.
 *
 * <p>Authorities are the role's permission codes (e.g. {@code USER_MANAGE})
 * plus {@code ROLE_<code>}. Code should check permissions, not roles.
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

    private final Long companyId;
    private final Long homeBranchId;
    private final boolean allBranches;
    private final Set<Long> branchIds;
    private final String roleCode;
    private final String roleName;
    private final long accessStamp;
    private final List<GrantedAuthority> authorities;

    public AppUserPrincipal(AppUser user, UserAccess access, Instant now, int passwordExpiryDays) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.fullName = user.getFullName();
        this.password = user.getPasswordHash();
        this.enabled = user.isEnabled();
        this.locked = user.isLockedAt(now);
        boolean expired = passwordExpiryDays > 0
                && user.getPasswordChangedAt().plusSeconds(passwordExpiryDays * 86_400L).isBefore(now);
        this.passwordChangeRequired = user.isMustChangePassword() || expired;

        this.companyId = user.getCompanyId();
        this.homeBranchId = user.getHomeBranchId();
        this.allBranches = user.isAllBranches();
        this.branchIds = Set.copyOf(user.getBranchIds());
        this.roleCode = access.roleCode();
        this.roleName = access.roleName();
        this.accessStamp = user.getAccessStamp();

        List<GrantedAuthority> list = new ArrayList<>();
        access.permissions().forEach(p -> list.add(new SimpleGrantedAuthority(p)));
        list.add(new SimpleGrantedAuthority("ROLE_" + access.roleCode()));
        this.authorities = List.copyOf(list);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    public boolean hasPermission(String permission) {
        return authorities.stream().anyMatch(a -> a.getAuthority().equals(permission));
    }

    /** May this user work with records of the given branch? */
    public boolean canAccessBranch(Long branchId) {
        return allBranches || branchIds.contains(branchId);
    }

    /** Never hold the hash in the session longer than needed. */
    @Override
    public void eraseCredentials() {
        this.password = null;
    }
}
