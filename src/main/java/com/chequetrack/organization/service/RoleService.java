package com.chequetrack.organization.service;

import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.event.Changes;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.common.exception.NotFoundException;
import com.chequetrack.organization.OrganizationPermissions;
import com.chequetrack.organization.domain.Permission;
import com.chequetrack.organization.domain.Role;
import com.chequetrack.organization.dto.PermissionGroup;
import com.chequetrack.organization.dto.RoleForm;
import com.chequetrack.organization.repository.PermissionRepository;
import com.chequetrack.organization.repository.RoleRepository;
import com.chequetrack.security.repository.AppUserRepository;
import com.chequetrack.security.service.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Roles and their permissions. Rules:
 * <ul>
 *   <li>the built-in ADMIN role always keeps every permission, so nobody can lock the company out;</li>
 *   <li>built-in roles keep their code and cannot be deleted;</li>
 *   <li>a role can only be deleted when no user has it;</li>
 *   <li>changing a role's permissions refreshes the sessions of its users at their next request.</li>
 * </ul>
 */
@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AppUserRepository userRepository;
    private final ApplicationEventPublisher events;

    public RoleService(RoleRepository roleRepository,
                       PermissionRepository permissionRepository,
                       AppUserRepository userRepository,
                       ApplicationEventPublisher events) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.events = events;
    }

    /** All roles of the company; also used to fill the role list on the user form. */
    @Transactional(readOnly = true)
    public List<Role> list() {
        return roleRepository.findByCompanyIdOrderByName(CurrentUser.get().getCompanyId());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + OrganizationPermissions.ROLE_MANAGE + "')")
    public Map<Long, Long> userCounts() {
        return list().stream().collect(Collectors.toMap(Role::getId, r -> userRepository.countByRoleId(r.getId())));
    }

    @Transactional(readOnly = true)
    public Role get(Long id) {
        return roleRepository.findByIdAndCompanyId(id, CurrentUser.get().getCompanyId())
                .orElseThrow(() -> new NotFoundException("Role", id));
    }

    /** The permission catalogue in display order, grouped. */
    @Transactional(readOnly = true)
    public List<PermissionGroup> permissionGroups() {
        Map<String, List<Permission>> groups = permissionRepository.findAllByOrderBySortOrder().stream()
                .collect(Collectors.groupingBy(Permission::getGroupName, LinkedHashMap::new, Collectors.toList()));
        return groups.entrySet().stream().map(e -> new PermissionGroup(e.getKey(), e.getValue())).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + OrganizationPermissions.ROLE_MANAGE + "')")
    public RoleForm editForm(Long id) {
        Role role = get(id);
        RoleForm form = new RoleForm();
        form.setId(role.getId());
        form.setVersion(role.getVersion());
        form.setCode(role.getCode());
        form.setName(role.getName());
        form.setDescription(role.getDescription());
        form.setPermissions(new HashSet<>(role.getPermissionCodes()));
        return form;
    }

    @Transactional
    @PreAuthorize("hasAuthority('" + OrganizationPermissions.ROLE_MANAGE + "')")
    public Role create(RoleForm form) {
        Long companyId = CurrentUser.get().getCompanyId();
        String code = form.getCode().trim().toUpperCase();
        if (roleRepository.existsByCompanyIdAndCodeIgnoreCase(companyId, code)) {
            throw new BusinessException("Role code '" + code + "' already exists.");
        }
        Role role = Role.create(companyId, code);
        role.update(form.getName(), form.getDescription());
        role.replacePermissions(validPermissions(form.getPermissions()));
        roleRepository.save(role);
        events.publishEvent(AuditEvent.of("ROLE_CREATED", "Role", role.getId(), "Created role " + role.getName(),
                new Changes().add("permissions", null, role.getPermissionCodes())));
        return role;
    }

    @Transactional
    @PreAuthorize("hasAuthority('" + OrganizationPermissions.ROLE_MANAGE + "')")
    public void update(Long id, RoleForm form) {
        Role role = get(id);
        role.checkVersion(form.getVersion());

        Set<String> permissions = validPermissions(form.getPermissions());
        if (OrganizationPermissions.ADMIN_ROLE.equals(role.getCode())) {
            permissions = allPermissionCodes();  // the administrator role can never lose a permission
        }
        Changes changes = new Changes()
                .add("name", role.getName(), form.getName())
                .add("description", role.getDescription(), form.getDescription())
                .add("permissions", role.getPermissionCodes(), permissions);

        role.update(form.getName(), form.getDescription());
        if (role.replacePermissions(permissions)) {
            userRepository.touchAccessForRole(role.getId());
        }
        if (!changes.isEmpty()) {
            events.publishEvent(AuditEvent.of("ROLE_UPDATED", "Role", role.getId(),
                    "Updated role " + role.getName(), changes));
        }
    }

    @Transactional
    @PreAuthorize("hasAuthority('" + OrganizationPermissions.ROLE_MANAGE + "')")
    public void delete(Long id) {
        Role role = get(id);
        if (role.isSystemRole()) {
            throw new BusinessException("Built-in role '" + role.getName() + "' cannot be deleted.");
        }
        long users = userRepository.countByRoleId(role.getId());
        if (users > 0) {
            throw new BusinessException("Role '" + role.getName() + "' is assigned to " + users
                    + " user(s). Give them another role first.");
        }
        role.markDeleted();
        events.publishEvent(AuditEvent.of("ROLE_DELETED", "Role", role.getId(), "Deleted role " + role.getName()));
    }

    private Set<String> validPermissions(Set<String> requested) {
        Set<String> known = allPermissionCodes();
        Set<String> result = new HashSet<>(requested == null ? Set.of() : requested);
        result.removeIf(code -> !known.contains(code));
        return result;
    }

    private Set<String> allPermissionCodes() {
        return permissionRepository.findAll().stream().map(Permission::getCode).collect(Collectors.toSet());
    }
}
