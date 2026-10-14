package com.chequetrack.audit.service;

import com.chequetrack.audit.domain.AuditLogEntry;
import com.chequetrack.audit.domain.StatusHistoryEntry;
import com.chequetrack.audit.repository.AuditLogRepository;
import com.chequetrack.audit.repository.StatusHistoryRepository;
import com.chequetrack.common.config.JpaAuditingConfig;
import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.event.StatusChangedEvent;
import com.chequetrack.security.domain.AppUser;
import com.chequetrack.security.repository.AppUserRepository;
import com.chequetrack.security.service.AppUserPrincipal;
import com.chequetrack.security.service.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.util.Optional;

/**
 * Writes audit log and status history rows.
 * <ul>
 *   <li>{@link AuditEvent} and {@link StatusChangedEvent} are written just before the publishing
 *       transaction commits, so they are saved together with the change or not at all. Events
 *       published outside a transaction are written immediately.</li>
 *   <li>Logins, failed logins and logouts come from Spring Security's own events.</li>
 * </ul>
 */
@Component
public class AuditRecorder {

    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGIN_REFUSED = "LOGIN_REFUSED";
    public static final String LOGOUT = "LOGOUT";

    private final AuditLogRepository auditLogRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final AppUserRepository userRepository;
    private final Clock clock;

    public AuditRecorder(AuditLogRepository auditLogRepository,
                         StatusHistoryRepository statusHistoryRepository,
                         AppUserRepository userRepository,
                         Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    // ---- events published by the modules -------------------------------------------------

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onAuditEvent(AuditEvent event) {
        Optional<AppUserPrincipal> actor = CurrentUser.find();
        auditLogRepository.save(AuditLogEntry.builder()
                .occurredAt(clock.instant())
                .companyId(actor.map(AppUserPrincipal::getCompanyId).orElse(null))
                .userId(actor.map(AppUserPrincipal::getId).orElse(null))
                .username(actor.map(AppUserPrincipal::getUsername).orElse(JpaAuditingConfig.SYSTEM_USER))
                .action(event.action())
                .entityType(event.entityType())
                .entityId(event.entityId() == null ? null : String.valueOf(event.entityId()))
                .summary(event.summary())
                .details(event.details())
                .ipAddress(clientIp())
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onStatusChanged(StatusChangedEvent event) {
        Optional<AppUserPrincipal> actor = CurrentUser.find();
        statusHistoryRepository.save(StatusHistoryEntry.builder()
                .companyId(event.companyId())
                .branchId(event.branchId())
                .entityType(event.entityType())
                .entityId(event.entityId())
                .fromStatus(event.fromStatus())
                .toStatus(event.toStatus())
                .reason(event.reason() == null || event.reason().isBlank() ? null : event.reason().trim())
                .changedAt(clock.instant())
                .userId(actor.map(AppUserPrincipal::getId).orElse(null))
                .username(actor.map(AppUserPrincipal::getUsername).orElse(JpaAuditingConfig.SYSTEM_USER))
                .build());
    }

    // ---- Spring Security events ---------------------------------------------------------------

    @EventListener
    public void onLoginSuccess(AuthenticationSuccessEvent event) {
        // Only real logins; per-request API token checks use another authentication type
        if (event.getAuthentication() instanceof UsernamePasswordAuthenticationToken
                && event.getAuthentication().getPrincipal() instanceof AppUserPrincipal user) {
            saveLoginEntry(LOGIN_SUCCESS, user.getCompanyId(), user.getId(), user.getUsername(),
                    "Logged in" + channel());
        }
    }

    @EventListener
    public void onLoginFailure(AbstractAuthenticationFailureEvent event) {
        String username = String.valueOf(event.getAuthentication().getPrincipal());
        Optional<AppUser> user = userRepository.findByUsername(username);
        String action;
        String summary;
        if (event instanceof AuthenticationFailureBadCredentialsEvent) {
            action = LOGIN_FAILED;
            summary = user.isPresent() ? "Wrong password" : "Unknown username";
        } else if (event instanceof AuthenticationFailureLockedEvent) {
            action = LOGIN_REFUSED;
            summary = "Login refused: account locked";
        } else if (event instanceof AuthenticationFailureDisabledEvent) {
            action = LOGIN_REFUSED;
            summary = "Login refused: account disabled";
        } else {
            action = LOGIN_FAILED;
            summary = "Login failed: " + event.getException().getClass().getSimpleName();
        }
        saveLoginEntry(action, user.map(AppUser::getCompanyId).orElse(null), user.map(AppUser::getId).orElse(null),
                truncate(username, 100), summary + channel());
    }

    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        if (event.getAuthentication() != null && event.getAuthentication().getPrincipal() instanceof AppUserPrincipal user) {
            saveLoginEntry(LOGOUT, user.getCompanyId(), user.getId(), user.getUsername(), "Logged out");
        }
    }

    private void saveLoginEntry(String action, Long companyId, Long userId, String username, String summary) {
        auditLogRepository.save(AuditLogEntry.builder()
                .occurredAt(clock.instant())
                .companyId(companyId)
                .userId(userId)
                .username(username)
                .action(action)
                .entityType(userId == null ? null : "User")
                .entityId(userId == null ? null : String.valueOf(userId))
                .summary(summary)
                .details(null)
                .ipAddress(clientIp())
                .build());
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static Optional<HttpServletRequest> currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? Optional.of(attrs.getRequest()) : Optional.empty();
    }

    private static String clientIp() {
        return currentRequest().map(HttpServletRequest::getRemoteAddr).orElse(null);
    }

    private static String channel() {
        return currentRequest()
                .map(r -> r.getRequestURI().startsWith(r.getContextPath() + "/api/") ? " (API)" : " (web)")
                .orElse("");
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
