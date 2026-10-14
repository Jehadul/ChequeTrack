package com.chequetrack.organization.scope;

import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.common.exception.NotFoundException;
import com.chequetrack.organization.domain.Branch;
import com.chequetrack.organization.repository.BranchRepository;
import com.chequetrack.security.service.AppUserPrincipal;
import com.chequetrack.security.service.CurrentUser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Per-record branch checks, complementing the query filters:
 * <ul>
 *   <li>{@link #require} after loading one record by id (filters don't cover that);</li>
 *   <li>{@link #branchForNewRecord} when a user creates a record for a branch.</li>
 * </ul>
 */
@Component
public class BranchScope {

    private final BranchRepository branchRepository;

    public BranchScope(BranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    /**
     * @return the record when the current user may see it
     * @throws NotFoundException otherwise: the same answer as a missing record, so nothing leaks
     */
    public <T extends BranchScopedEntity> T require(Optional<T> record, String entityName, Object id) {
        AppUserPrincipal user = CurrentUser.get();
        return record
                .filter(r -> r.getCompanyId().equals(user.getCompanyId()) && user.canAccessBranch(r.getBranchId()))
                .orElseThrow(() -> new NotFoundException(entityName, id));
    }

    /**
     * Branch for a new record: the chosen one, or the user's home branch when none is chosen.
     * It must be active and one the user may work with.
     */
    public Long branchForNewRecord(Long requestedBranchId) {
        AppUserPrincipal user = CurrentUser.get();
        Long branchId = requestedBranchId != null ? requestedBranchId : user.getHomeBranchId();
        Branch branch = branchRepository.findByIdAndCompanyId(branchId, user.getCompanyId())
                .filter(b -> user.canAccessBranch(b.getId()))
                .orElseThrow(() -> new BusinessException("Choose one of your branches."));
        if (!branch.isActive()) {
            throw new BusinessException("Branch " + branch.getCode() + " is inactive.");
        }
        return branch.getId();
    }

    /** Active branches the current user may create records for (for drop-downs). */
    public List<Branch> selectableBranches() {
        AppUserPrincipal user = CurrentUser.get();
        return branchRepository.findByCompanyIdAndActiveTrueOrderByCode(user.getCompanyId()).stream()
                .filter(b -> user.canAccessBranch(b.getId()))
                .toList();
    }
}
