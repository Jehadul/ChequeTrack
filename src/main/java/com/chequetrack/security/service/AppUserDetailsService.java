package com.chequetrack.security.service;

import com.chequetrack.security.config.SecurityProperties;
import com.chequetrack.security.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;
    private final SecurityProperties properties;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public AppUserPrincipal loadUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(user -> new AppUserPrincipal(user, clock.instant(), properties.password().expiryDays()))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
