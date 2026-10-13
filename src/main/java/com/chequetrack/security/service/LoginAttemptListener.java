package com.chequetrack.security.service;

import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.security.config.SecurityProperties;
import com.chequetrack.security.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Counts failed logins and locks the account when the limit is reached.
 * Applies to both the web login form and the API login, because both use the
 * same AuthenticationManager. Requests made with an API token are not logins
 * and are ignored here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptListener {

    private final AppUserRepository userRepository;
    private final SecurityProperties properties;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    @Transactional
    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        String username = String.valueOf(event.getAuthentication().getPrincipal());
        Instant lockUntil = clock.instant().plus(Duration.ofMinutes(properties.lockout().lockMinutes()));
        userRepository.registerFailedLogin(username, properties.lockout().maxFailedAttempts(), lockUntil);
        log.info("Failed login for '{}'", username);

        // The counter restarts at 0 when the lock is applied, so "locked and 0 attempts" means "just locked"
        userRepository.findByUsername(username)
                .filter(u -> u.isLockedAt(clock.instant()) && u.getFailedLoginAttempts() == 0)
                .ifPresent(u -> events.publishEvent(AuditEvent.of("ACCOUNT_LOCKED", "User", u.getId(),
                        "Account '" + u.getUsername() + "' locked for " + properties.lockout().lockMinutes()
                                + " minutes after " + properties.lockout().maxFailedAttempts() + " failed logins")));
    }

    @Transactional
    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication() instanceof UsernamePasswordAuthenticationToken
                && event.getAuthentication().getPrincipal() instanceof AppUserPrincipal user) {
            userRepository.registerSuccessfulLogin(user.getId(), clock.instant());
            log.info("User '{}' logged in", user.getUsername());
        }
    }
}
