package com.chequetrack.organization.service;

import com.chequetrack.organization.OrganizationPermissions;
import com.chequetrack.organization.domain.Branch;
import com.chequetrack.organization.domain.Company;
import com.chequetrack.organization.domain.Role;
import com.chequetrack.organization.repository.BranchRepository;
import com.chequetrack.organization.repository.CompanyRepository;
import com.chequetrack.organization.repository.RoleRepository;
import com.chequetrack.security.config.SecurityProperties;
import com.chequetrack.security.domain.AppUser;
import com.chequetrack.security.repository.AppUserRepository;
import com.chequetrack.security.service.AccountService;
import com.chequetrack.security.service.NewUserAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Creates the first administrator when no users exist, so a fresh install can
 * be logged into. Uses the company, head office and ADMIN role seeded by V3.
 * The password is temporary and must be changed at first login.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitialSetup implements ApplicationRunner {

    private final AppUserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final BranchRepository branchRepository;
    private final RoleRepository roleRepository;
    private final AccountService accountService;
    private final UserManagementService userManagementService;
    private final SecurityProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        Company company = companyRepository.findByCode("MAIN")
                .orElseThrow(() -> new IllegalStateException("Seed company MAIN missing (migration V3)"));
        Branch headOffice = branchRepository.findByCompanyIdAndCodeIgnoreCase(company.getId(), "HO")
                .orElseThrow(() -> new IllegalStateException("Seed branch HO missing (migration V3)"));
        Role admin = roleRepository.findByCompanyIdAndCodeIgnoreCase(company.getId(), OrganizationPermissions.ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException("Seed role ADMIN missing (migration V3)"));

        SecurityProperties.Bootstrap bootstrap = properties.bootstrap();
        boolean generated = bootstrap.adminPassword() == null || bootstrap.adminPassword().isBlank();
        String password = generated ? accountService.generateTemporaryPassword() : bootstrap.adminPassword();

        AppUser user = accountService.createUser(new NewUserAccount(bootstrap.adminUsername(), "System Administrator", null,
                password, company.getId(), admin.getId(), headOffice.getId(), true, Set.of()));
        userManagementService.recordCreated(user);

        if (generated) {
            log.warn("Created first administrator '{}' with temporary password: {}  (change it at first login)",
                    bootstrap.adminUsername(), password);
        } else {
            log.warn("Created first administrator '{}' with the configured temporary password "
                    + "(change it at first login)", bootstrap.adminUsername());
        }
    }
}
