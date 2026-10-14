package com.chequetrack.organization.service;

import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.event.Changes;
import com.chequetrack.common.event.StatusChangedEvent;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.common.exception.NotFoundException;
import com.chequetrack.organization.OrganizationPermissions;
import com.chequetrack.organization.domain.Branch;
import com.chequetrack.organization.domain.Role;
import com.chequetrack.organization.dto.UserForm;
import com.chequetrack.organization.dto.UserView;
import com.chequetrack.organization.repository.BranchRepository;
import com.chequetrack.organization.repository.RoleRepository;
import com.chequetrack.security.domain.AppUser;
import com.chequetrack.security.repository.AppUserRepository;
import com.chequetrack.security.service.AccountService;
import com.chequetrack.security.service.AppUserPrincipal;
import com.chequetrack.security.service.CurrentUser;
import com.chequetrack.security.service.NewUserAccount;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Managing other users. Visibility follows branch access: a user without
 * "all branches" only sees users whose home branch is one of theirs.
 * Administrators cannot disable themselves or change their own access,
 * so they cannot lock themselves out by mistake.
 */
@Service
public class UserManagementService {

    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BranchRepository branchRepository;
    private final AccountService accountService;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public UserManagementService(AppUserRepository userRepository,
                                 RoleRepository roleRepository,
                                 BranchRepository branchRepository,
                                 AccountService accountService,
                                 Clock clock,
                                 ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.branchRepository = branchRepository;
        this.accountService = accountService;
        this.clock = clock;
        this.events = events;
    }

    static final String ENTITY = "User";
    static final String STATUS_ACTIVE = "ACTIVE";
    static final String STATUS_DISABLED = "DISABLED";

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + OrganizationPermissions.USER_VIEW + "')")
    public List<UserView> listVisible() {
        AppUserPrincipal me = CurrentUser.get();
        Names names = names(me.getCompanyId());
        return userRepository.findByCompanyIdOrderByUsername(me.getCompanyId()).stream()
                .filter(u -> me.canAccessBranch(u.getHomeBranchId()))
                .map(u -> toView(u, names))
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + OrganizationPermissions.USER_VIEW + "')")
    public UserView view(Long id) {
        return toView(getVisible(id), names(CurrentUser.get().getCompanyId()));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + OrganizationPermissions.USER_MANAGE + "')")
    public UserForm editForm(Long id) {
        AppUser user = getVisible(id);
        UserForm form = new UserForm();
        form.setId(user.getId());
        form.setVersion(user.getVersion());
        form.setUsername(user.getUsername());
        form.setFullName(user.getFullName());
        form.setEmail(user.getEmail());
        form.setRoleId(user.getRoleId());
        form.setHomeBranchId(user.getHomeBranchId());
        form.setAllBranches(user.isAllBranches());
        form.setBranchIds(new HashSet<>(user.getBranchIds()));
        return form;
    }

    /** @return the temporary password, shown once to the administrator */
    @Transactional
    @PreAuthorize("hasAuthority('" + OrganizationPermissions.USER_MANAGE + "')")
    public CreatedUser create(UserForm form) {
        if (form.getUsername() == null || form.getUsername().isBlank()) {
            throw new BusinessException("Enter a username.");
        }
        Long companyId = CurrentUser.get().getCompanyId();
        validateAccess(companyId, form);
        String temporaryPassword = accountService.generateTemporaryPassword();
        AppUser user = accountService.createUser(new NewUserAccount(form.getUsername(), form.getFullName(),
                form.getEmail(), temporaryPassword, companyId, form.getRoleId(), form.getHomeBranchId(),
                form.isAllBranches(), safe(form.getBranchIds())));
        recordCreated(user);
        return new CreatedUser(user.getId(), user.getUsername(), temporaryPassword);
    }

    /**
     * Audit entry and first status history row of a new user. Package-private: also
     * used by {@link InitialSetup} for the first administrator.
     */
    void recordCreated(AppUser user) {
        Names names = names(user.getCompanyId());
        events.publishEvent(AuditEvent.of("USER_CREATED", ENTITY, user.getId(),
                "Created user " + user.getUsername() + " (" + names.roles().get(user.getRoleId()) + ")",
                new Changes()
                        .add("fullName", null, user.getFullName())
                        .add("email", null, user.getEmail())
                        .add("role", null, names.roles().get(user.getRoleId()))
                        .add("homeBranch", null, names.branches().get(user.getHomeBranchId()))
                        .add("allBranches", null, user.isAllBranches())
                        .add("branches", null, names.branchCodes(user.getBranchIds()))));
        events.publishEvent(new StatusChangedEvent(ENTITY, user.getId(), user.getCompanyId(), user.getHomeBranchId(),
                null, STATUS_ACTIVE, "User created"));
    }

    @Transactional
    @PreAuthorize("hasAuthority('" + OrganizationPermissions.USER_MANAGE + "')")
    public void update(Long id, UserForm form) {
        AppUser user = getVisible(id);
        user.checkVersion(form.getVersion());
        validateAccess(user.getCompanyId(), form);
        if (isSelf(user) && accessChanged(user, form)) {
            throw new BusinessException("You cannot change your own role or branch access. Ask another administrator.");
        }
        Names names = names(user.getCompanyId());
        Set<Long> newBranches = new HashSet<>(safe(form.getBranchIds()));
        newBranches.add(form.getHomeBranchId());
        Changes changes = new Changes()
                .add("fullName", user.getFullName(), form.getFullName())
                .add("email", user.getEmail(), form.getEmail() == null ? null : form.getEmail().toLowerCase())
                .add("role", names.roles().get(user.getRoleId()), names.roles().get(form.getRoleId()))
                .add("homeBranch", names.branches().get(user.getHomeBranchId()), names.branches().get(form.getHomeBranchId()))
                .add("allBranches", user.isAllBranches(), form.isAllBranches())
                .add("branches", names.branchCodes(user.getBranchIds()), names.branchCodes(newBranches));

        user.updateProfile(form.getFullName(), form.getEmail());
        user.assignAccess(form.getRoleId(), form.getHomeBranchId(), form.isAllBranches(), safe(form.getBranchIds()));
        if (!changes.isEmpty()) {
            events.publishEvent(AuditEvent.of("USER_UPDATED", ENTITY, user.getId(),
                    "Updated user " + user.getUsername(), changes));
        }
    }

    /**
     * Enables or disables a user. Disabling needs a reason; both are kept in the
     * user's status history.
     */
    @Transactional
    @PreAuthorize("hasAuthority('" + OrganizationPermissions.USER_MANAGE + "')")
    public void setEnabled(Long id, boolean enabled, String reason) {
        AppUser user = getVisible(id);
        if (isSelf(user) && !enabled) {
            throw new BusinessException("You cannot disable your own account.");
        }
        if (!enabled && (reason == null || reason.isBlank())) {
            throw new BusinessException("Enter the reason for disabling this user.");
        }
        if (reason != null && reason.length() > 300) {
            throw new BusinessException("The reason must be at most 300 characters.");
        }
        if (user.isEnabled() == enabled) {
            return;
        }
        user.setEnabled(enabled);
        String from = enabled ? STATUS_DISABLED : STATUS_ACTIVE;
        String to = enabled ? STATUS_ACTIVE : STATUS_DISABLED;
        events.publishEvent(new StatusChangedEvent(ENTITY, user.getId(), user.getCompanyId(), user.getHomeBranchId(),
                from, to, reason));
        events.publishEvent(AuditEvent.of(enabled ? "USER_ENABLED" : "USER_DISABLED", ENTITY, user.getId(),
                (enabled ? "Enabled" : "Disabled") + " user " + user.getUsername()
                        + (reason == null || reason.isBlank() ? "" : ": " + reason.trim())));
    }

    /** @return the new temporary password, shown once to the administrator */
    @Transactional
    @PreAuthorize("hasAuthority('" + OrganizationPermissions.USER_MANAGE + "')")
    public String resetPassword(Long id) {
        AppUser user = getVisible(id);
        if (isSelf(user)) {
            throw new BusinessException("Use 'Change password' to change your own password.");
        }
        return accountService.resetPassword(user.getId());
    }

    @Transactional
    @PreAuthorize("hasAuthority('" + OrganizationPermissions.USER_MANAGE + "')")
    public void unlock(Long id) {
        AppUser user = getVisible(id);
        if (user.isLockedAt(clock.instant())) {
            user.unlock();
            events.publishEvent(AuditEvent.of("USER_UNLOCKED", ENTITY, user.getId(), "Unlocked user " + user.getUsername()));
        }
    }

    // ------------------------------------------------------------------------------------------

    private AppUser getVisible(Long id) {
        AppUserPrincipal me = CurrentUser.get();
        AppUser user = userRepository.findByIdAndCompanyId(id, me.getCompanyId())
                .orElseThrow(() -> new NotFoundException("User", id));
        if (!me.canAccessBranch(user.getHomeBranchId())) {
            throw new NotFoundException("User", id);  // same answer as "does not exist": no information leak
        }
        return user;
    }

    /** Role and branches must belong to the company; branches must be active. */
    private void validateAccess(Long companyId, UserForm form) {
        roleRepository.findByIdAndCompanyId(form.getRoleId(), companyId)
                .orElseThrow(() -> new BusinessException("Choose a valid role."));
        Set<Long> wanted = new HashSet<>(safe(form.getBranchIds()));
        wanted.add(form.getHomeBranchId());
        List<Branch> found = branchRepository.findByCompanyIdAndIdInOrderByCode(companyId, wanted);
        if (found.size() != wanted.size()) {
            throw new BusinessException("Choose valid branches.");
        }
        found.stream().filter(b -> !b.isActive()).findFirst().ifPresent(b -> {
            throw new BusinessException("Branch " + b.getCode() + " is inactive.");
        });
    }

    private boolean accessChanged(AppUser user, UserForm form) {
        Set<Long> wanted = new HashSet<>(safe(form.getBranchIds()));
        wanted.add(form.getHomeBranchId());
        return !user.getRoleId().equals(form.getRoleId())
                || !user.getHomeBranchId().equals(form.getHomeBranchId())
                || user.isAllBranches() != form.isAllBranches()
                || !user.getBranchIds().equals(wanted);
    }

    private static boolean isSelf(AppUser user) {
        return user.getId().equals(CurrentUser.get().getId());
    }

    private static Set<Long> safe(Set<Long> ids) {
        return ids == null ? Set.of() : ids;
    }

    private UserView toView(AppUser u, Names names) {
        List<String> branches = u.getBranchIds().stream()
                .map(id -> names.branches().getOrDefault(id, "?"))
                .sorted(Comparator.naturalOrder())
                .toList();
        LocalDateTime lastLogin = u.getLastLoginAt() == null ? null
                : LocalDateTime.ofInstant(u.getLastLoginAt(), clock.getZone());
        return new UserView(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(),
                names.roles().getOrDefault(u.getRoleId(), "?"),
                names.branches().getOrDefault(u.getHomeBranchId(), "?"),
                u.isAllBranches(), branches, u.isEnabled(), u.isLockedAt(clock.instant()),
                u.isMustChangePassword(), lastLogin);
    }

    private Names names(Long companyId) {
        Map<Long, String> roles = roleRepository.findByCompanyIdOrderByName(companyId).stream()
                .collect(Collectors.toMap(Role::getId, Role::getName));
        Map<Long, String> branches = branchRepository.findByCompanyIdOrderByCode(companyId).stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getCode, (a, b) -> a));
        return new Names(roles, branches);
    }

    private record Names(Map<Long, String> roles, Map<Long, String> branches) {

        /** Sorted branch codes, for readable audit details. */
        List<String> branchCodes(Set<Long> ids) {
            return ids.stream().map(id -> branches.getOrDefault(id, "?")).sorted().toList();
        }
    }

    public record CreatedUser(Long id, String username, String temporaryPassword) {
    }
}
