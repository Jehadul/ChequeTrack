package com.chequetrack.security.service;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Access to the logged-in user from service code. */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** @throws AuthenticationCredentialsNotFoundException when nobody is logged in */
    public static AppUserPrincipal get() {
        return find().orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Not logged in"));
    }

    public static Optional<AppUserPrincipal> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }
}
