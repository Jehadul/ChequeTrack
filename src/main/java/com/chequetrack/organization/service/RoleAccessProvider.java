package com.chequetrack.organization.service;

import com.chequetrack.organization.repository.RoleRepository;
import com.chequetrack.security.service.UserAccess;
import com.chequetrack.security.service.UserAccessProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/** Supplies role and permissions to the security module at login and on every API call. */
@Service
public class RoleAccessProvider implements UserAccessProvider {

    private final RoleRepository roleRepository;

    public RoleAccessProvider(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserAccess forRole(Long roleId) {
        return roleRepository.findById(roleId)
                .map(r -> new UserAccess(r.getCode(), r.getName(), Set.copyOf(r.getPermissionCodes())))
                .orElse(UserAccess.NONE);
    }
}
