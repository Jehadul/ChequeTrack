package com.chequetrack.security.service;

import com.chequetrack.security.config.SecurityProperties;
import com.chequetrack.security.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first administrator when no users exist, so a fresh install can
 * be logged into. The password is temporary and must be changed at first login.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final AppUserRepository userRepository;
    private final AccountService accountService;
    private final SecurityProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        SecurityProperties.Bootstrap bootstrap = properties.bootstrap();
        boolean generated = bootstrap.adminPassword() == null || bootstrap.adminPassword().isBlank();
        String password = generated ? accountService.generateTemporaryPassword() : bootstrap.adminPassword();

        accountService.createUser(bootstrap.adminUsername(), "System Administrator", null, password);

        if (generated) {
            log.warn("Created first administrator '{}' with temporary password: {}  (change it at first login)",
                    bootstrap.adminUsername(), password);
        } else {
            log.warn("Created first administrator '{}' with the configured temporary password "
                    + "(change it at first login)", bootstrap.adminUsername());
        }
    }
}
