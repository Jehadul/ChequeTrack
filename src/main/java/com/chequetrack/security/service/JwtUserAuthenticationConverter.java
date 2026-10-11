package com.chequetrack.security.service;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Turns a verified JWT into the current user. The user is reloaded on every
 * request, so disabling or locking an account takes effect immediately
 * instead of when the token expires.
 */
@Component
@RequiredArgsConstructor
public class JwtUserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AppUserDetailsService userDetailsService;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AppUserPrincipal user;
        try {
            user = userDetailsService.loadUserByUsername(jwt.getSubject());
        } catch (UsernameNotFoundException ex) {
            throw new BadCredentialsException("Token user no longer exists");
        }
        if (!user.isEnabled()) {
            throw new DisabledException("Account is disabled");
        }
        if (!user.isAccountNonLocked()) {
            throw new LockedException("Account is locked");
        }
        if (user.isPasswordChangeRequired()) {
            throw new CredentialsExpiredException("Password change required");
        }
        user.eraseCredentials();
        return new ApiTokenAuthentication(user, jwt);
    }
}
