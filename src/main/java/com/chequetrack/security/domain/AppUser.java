package com.chequetrack.security.domain;

import com.chequetrack.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

/**
 * A person who can log in. Login counters (failed attempts, lock, last login)
 * are changed with direct UPDATE statements in {@code AppUserRepository} so
 * that concurrent logins never clash with the optimistic-lock version.
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

    /** New user with a temporary password that must be changed at first login. */
    public static AppUser create(String username, String fullName, String email, String passwordHash, Instant now) {
        AppUser user = new AppUser();
        user.username = normalizeUsername(username);
        user.fullName = fullName.trim();
        user.email = email == null || email.isBlank() ? null : email.trim().toLowerCase();
        user.passwordHash = passwordHash;
        user.passwordChangedAt = now;
        user.mustChangePassword = true;
        return user;
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
    }

    public void unlock() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public static String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase();
    }
}
