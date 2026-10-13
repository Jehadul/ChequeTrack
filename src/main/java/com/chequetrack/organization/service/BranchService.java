package com.chequetrack.organization.service;

import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.event.Changes;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.common.exception.NotFoundException;
import com.chequetrack.organization.OrganizationPermissions;
import com.chequetrack.organization.domain.Branch;
import com.chequetrack.organization.dto.BranchForm;
import com.chequetrack.organization.repository.BranchRepository;
import com.chequetrack.security.repository.AppUserRepository;
import com.chequetrack.security.service.AppUserPrincipal;
import com.chequetrack.security.service.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Branches of the user's company. Users without "all branches" access only see
 * the branches assigned to them; anything else is reported as not found.
 */
@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;
    private final AppUserRepository userRepository;
    private final ApplicationEventPublisher events;

    /** Branches the current user may see. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + OrganizationPermissions.BRANCH_VIEW + "')")
    public List<Branch> listVisible() {
        AppUserPrincipal user = CurrentUser.get();
        return user.isAllBranches()
                ? branchRepository.findByCompanyIdOrderByCode(user.getCompanyId())
                : branchRepository.findByCompanyIdAndIdInOrderByCode(user.getCompanyId(), user.getBranchIds());
    }

    /** Active branches of the company, for choosing a user's branches. */
    @Transactional(readOnly = true)
    public List<Branch> listActiveInCompany() {
        return branchRepository.findByCompanyIdAndActiveTrueOrderByCode(CurrentUser.get().getCompanyId());
    }

    @Transactional(readOnly = true)
    public List<Branch> listAllInCompany() {
        return branchRepository.findByCompanyIdOrderByCode(CurrentUser.get().getCompanyId());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + OrganizationPermissions.BRANCH_VIEW + "')")
    public Branch get(Long id) {
        AppUserPrincipal user = CurrentUser.get();
        Branch branch = branchRepository.findByIdAndCompanyId(id, user.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Branch", id));
        if (!user.canAccessBranch(branch.getId())) {
            throw new NotFoundException("Branch", id);
        }
        return branch;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + OrganizationPermissions.BRANCH_MANAGE + "')")
    public BranchForm editForm(Long id) {
        Branch branch = get(id);
        BranchForm form = new BranchForm();
        form.setId(branch.getId());
        form.setVersion(branch.getVersion());
        form.setCode(branch.getCode());
        form.setName(branch.getName());
        form.setAddress(branch.getAddress());
        form.setPhone(branch.getPhone());
        form.setEmail(branch.getEmail());
        return form;
    }

    @Transactional
    @PreAuthorize("hasAuthority('" + OrganizationPermissions.BRANCH_MANAGE + "')")
    public Branch create(BranchForm form) {
        Long companyId = CurrentUser.get().getCompanyId();
        if (branchRepository.existsByCompanyIdAndCodeIgnoreCase(companyId, form.getCode().trim())) {
            throw new BusinessException("Branch code '" + form.getCode().trim().toUpperCase() + "' already exists.");
        }
        Branch branch = Branch.create(companyId, form.getCode());
        branch.update(form.getName(), form.getAddress(), form.getPhone(), form.getEmail());
        branchRepository.save(branch);
        events.publishEvent(AuditEvent.of("BRANCH_CREATED", "Branch", branch.getId(),
                "Created branch " + branch.getLabel()));
        return branch;
    }

    /** The code is fixed once created, because other records refer to it on paper. */
    @Transactional
    @PreAuthorize("hasAuthority('" + OrganizationPermissions.BRANCH_MANAGE + "')")
    public void update(Long id, BranchForm form) {
        Branch branch = get(id);
        branch.checkVersion(form.getVersion());
        Changes changes = new Changes()
                .add("name", branch.getName(), form.getName())
                .add("address", branch.getAddress(), form.getAddress())
                .add("phone", branch.getPhone(), form.getPhone())
                .add("email", branch.getEmail(), form.getEmail());
        branch.update(form.getName(), form.getAddress(), form.getPhone(), form.getEmail());
        if (!changes.isEmpty()) {
            events.publishEvent(AuditEvent.of("BRANCH_UPDATED", "Branch", branch.getId(),
                    "Updated branch " + branch.getCode(), changes));
        }
    }

    @Transactional
    @PreAuthorize("hasAuthority('" + OrganizationPermissions.BRANCH_MANAGE + "')")
    public void setActive(Long id, boolean active) {
        Branch branch = get(id);
        if (!active) {
            long users = userRepository.countActiveWithHomeBranch(branch.getId());
            if (users > 0) {
                throw new BusinessException("Branch " + branch.getCode() + " is the home branch of " + users
                        + " active user(s). Move or disable them first.");
            }
        }
        if (branch.isActive() != active) {
            branch.setActive(active);
            events.publishEvent(AuditEvent.of(active ? "BRANCH_ACTIVATED" : "BRANCH_DEACTIVATED", "Branch",
                    branch.getId(), (active ? "Activated" : "Deactivated") + " branch " + branch.getLabel()));
        }
    }
}
