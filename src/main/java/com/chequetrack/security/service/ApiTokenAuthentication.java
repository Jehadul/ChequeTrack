package com.chequetrack.security.service;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Authentication for one API request carrying a valid JWT. Its own type, so
 * per-request token checks are never mistaken for a login (see LoginAttemptListener).
 */
public class ApiTokenAuthentication extends AbstractAuthenticationToken {

    private final AppUserPrincipal principal;
    private final transient Jwt token;

    public ApiTokenAuthentication(AppUserPrincipal principal, Jwt token) {
        super(principal.getAuthorities());
        this.principal = principal;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public AppUserPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public Jwt getCredentials() {
        return token;
    }
}
