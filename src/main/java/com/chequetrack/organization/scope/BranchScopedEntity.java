package com.chequetrack.organization.scope;

import com.chequetrack.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.util.Objects;

/**
 * Parent of every record that belongs to one branch (company bank accounts,
 * invoices, cheques, ...). Two Hibernate filters, switched on for every
 * transaction by {@link BranchScopeTransactionManager}, limit every query:
 * <ul>
 *   <li>{@value #COMPANY_FILTER}: the user's company (users with "all branches");</li>
 *   <li>{@value #BRANCH_FILTER}: the user's company and allowed branches (everyone else).</li>
 * </ul>
 * Filters do not apply to loading one record by id; services use
 * {@link BranchScope#require} for that.
 *
 * <p>Tables need {@code company_id BIGINT NOT NULL} and {@code branch_id BIGINT NOT NULL}.
 */
@Getter
@MappedSuperclass
@FilterDef(name = BranchScopedEntity.COMPANY_FILTER,
        parameters = @ParamDef(name = "companyId", type = Long.class))
@FilterDef(name = BranchScopedEntity.BRANCH_FILTER,
        parameters = {@ParamDef(name = "companyId", type = Long.class),
                      @ParamDef(name = "branchIds", type = Long.class)})
@Filter(name = BranchScopedEntity.COMPANY_FILTER, condition = "company_id = :companyId")
@Filter(name = BranchScopedEntity.BRANCH_FILTER, condition = "company_id = :companyId and branch_id in (:branchIds)")
public abstract class BranchScopedEntity extends BaseEntity {

    public static final String COMPANY_FILTER = "companyScope";
    public static final String BRANCH_FILTER = "branchScope";

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    protected void assignScope(Long companyId, Long branchId) {
        this.companyId = Objects.requireNonNull(companyId);
        this.branchId = Objects.requireNonNull(branchId);
    }

    protected void moveToBranch(Long branchId) {
        this.branchId = Objects.requireNonNull(branchId);
    }
}
