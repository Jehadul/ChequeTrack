package com.chequetrack.security.web;

import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.security.repository.AppUserRepository;
import com.chequetrack.security.service.AppUserDetailsService;
import com.chequetrack.security.service.AppUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Keeps web sessions in step with the database. On each page request it reads
 * the user's {@code enabled} flag and access stamp (one primary-key lookup):
 * <ul>
 *   <li>user disabled or deleted: the session is ended and the login page shown;</li>
 *   <li>access stamp changed (role, branches, password reset): the session's
 *       user snapshot is reloaded, so new permissions apply immediately.</li>
 * </ul>
 * Not a Spring bean on purpose: it is added only to the web filter chain in SecurityConfig.
 */
@Slf4j
public class SessionRefreshFilter extends OncePerRequestFilter {

    private static final String[] SKIPPED_PREFIXES = {"/css/", "/js/", "/images/", "/webjars/", "/favicon.ico", "/error"};

    private final AppUserRepository userRepository;
    private final AppUserDetailsService userDetailsService;
    private final ApplicationEventPublisher events;

    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public SessionRefreshFilter(AppUserRepository userRepository,
                                AppUserDetailsService userDetailsService,
                                ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.userDetailsService = userDetailsService;
        this.events = events;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (String prefix : SKIPPED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal user) {
            Optional<AppUserRepository.SessionCheck> check = userRepository.findSessionCheck(user.getId());

            if (check.isEmpty() || !check.get().isEnabled()) {
                log.info("Ending session of disabled or removed user '{}'", user.getUsername());
                events.publishEvent(AuditEvent.of("SESSION_ENDED", "User", user.getId(),
                        "Open session of '" + user.getUsername() + "' ended: account disabled"));
                new SecurityContextLogoutHandler().logout(request, response, auth);
                response.sendRedirect(request.getContextPath() + "/login?error=disabled");
                return;
            }
            if (check.get().getAccessStamp() != user.getAccessStamp()) {
                AppUserPrincipal fresh = userDetailsService.loadUserById(user.getId());
                fresh.eraseCredentials();
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(fresh, null, fresh.getAuthorities()));
                SecurityContextHolder.setContext(context);
                contextRepository.saveContext(context, request, response);
                log.debug("Refreshed session of user '{}'", user.getUsername());
            }
        }
        chain.doFilter(request, response);
    }
}
