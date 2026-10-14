package com.chequetrack.security.service;

import com.chequetrack.security.config.SecurityProperties;
import com.chequetrack.security.domain.AppUser;
import com.chequetrack.security.repository.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;
    private final UserAccessProvider accessProvider;
    private final SecurityProperties properties;
    private final Clock clock;

    public AppUserDetailsService(AppUserRepository userRepository,
                                 UserAccessProvider accessProvider,
                                 SecurityProperties properties,
                                 Clock clock) {
        this.userRepository = userRepository;
        this.accessProvider = accessProvider;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public AppUserPrincipal loadUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(this::toPrincipal)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }

    @Transactional(readOnly = true)
    public AppUserPrincipal loadUserById(Long id) {
        return userRepository.findById(id)
                .map(this::toPrincipal)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }

    private AppUserPrincipal toPrincipal(AppUser user) {
        UserAccess access = accessProvider.forRole(user.getRoleId());
        return new AppUserPrincipal(user, access, clock.instant(), properties.password().expiryDays());
    }
}
