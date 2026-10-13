package com.chequetrack.security.domain;

import com.chequetrack.common.domain.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A person who can log in. Login counters (failed attempts, lock, last login)
 * are changed with direct UPDATE statements in {@code AppUserRepository} so
 * that concurrent logins never clash with the optimistic-lock version.
 *
 * <p>Company, role and branches are stored as plain ids: the organization
 * module (M2) owns those tables, and this module must not depend on it.
 */
@Getter
@Entity
@Table(name = "app_user")
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String username;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "home_branch_id", nullable = false)
    private Long homeBranchId;

    /** May see every branch of the company. */
    @Column(name = "all_branches", nullable = false)
    private boolean allBranches;

    /** Branches this user may work with. Always contains the home branch. */
    @ElementCollection
    @CollectionTable(name = "user_branch", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "branch_id")
    private Set<Long> branchIds = new HashSet<>();

    /**
     * Changes whenever something that affects what the user may do changes.
     * Open sessions compare it on every request and refresh or end themselves.
     */
    @Column(name = "access_stamp", nullable = false)
    private long accessStamp;

    /** New user with a temporary password that must be changed at first login. */
    public static AppUser create(String username, String fullName, String email, String passwordHash,
                                 Long companyId, Instant now) {
        AppUser user = new AppUser();
        user.username = normalizeUsername(username);
        user.passwordHash = passwordHash;
        user.passwordChangedAt = now;
        user.mustChangePassword = true;
        user.companyId = Objects.requireNonNull(companyId);
        user.updateProfile(fullName, email);
        return user;
    }

    public void updateProfile(String fullName, String email) {
        this.fullName = fullName.trim();
        this.email = email == null || email.isBlank() ? null : email.trim().toLowerCase();
    }

    /** Sets role and branch access. The home branch is always one of the allowed branches. */
    public void assignAccess(Long roleId, Long homeBranchId, boolean allBranches, Collection<Long> branchIds) {
        Set<Long> newBranches = new HashSet<>(branchIds == null ? Set.of() : branchIds);
        newBranches.add(Objects.requireNonNull(homeBranchId));
        boolean changed = !Objects.equals(this.roleId, roleId)
                || !Objects.equals(this.homeBranchId, homeBranchId)
                || this.allBranches != allBranches
                || !this.branchIds.equals(newBranches);
        this.roleId = Objects.requireNonNull(roleId);
        this.homeBranchId = homeBranchId;
        this.allBranches = allBranches;
        this.branchIds.clear();
        this.branchIds.addAll(newBranches);
        if (changed) {
            touchAccess();
        }
    }

    /** The user chose a new password. */
    public void changePassword(String newHash, Instant now) {
        this.passwordHash = newHash;
        this.passwordChangedAt = now;
        this.mustChangePassword = false;
    }

    /** An administrator set a temporary password; the user must change it at next login. */
    public void resetPassword(String temporaryHash, Instant now) {
        this.passwordHash = temporaryHash;
        this.passwordChangedAt = now;
        this.mustChangePassword = true;
        unlock();
        touchAccess();
    }

    public void unlock() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            touchAccess();
        }
    }

    public void touchAccess() {
        this.accessStamp++;
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public boolean canAccessBranch(Long branchId) {
        return allBranches || branchIds.contains(branchId);
    }

    public static String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase();
    }
}
