package com.chequetrack.organization.scope;

import com.chequetrack.security.service.AppUserPrincipal;
import com.chequetrack.security.service.CurrentUser;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.Set;

/**
 * The application's transaction manager. After a transaction starts it switches on
 * the branch filters for the logged-in user, so every query on a
 * {@link BranchScopedEntity} is limited to what that user may see, even if a
 * repository method forgets the WHERE clause.
 *
 * <p>Work without a logged-in user (startup, scheduled jobs) is not filtered;
 * that code must choose its scope explicitly.
 */
public class BranchScopeTransactionManager extends JpaTransactionManager {

    public BranchScopeTransactionManager(EntityManagerFactory emf) {
        super(emf);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        Optional<AppUserPrincipal> user = CurrentUser.find();
        if (user.isEmpty()) {
            return;
        }
        EntityManagerHolder holder =
                (EntityManagerHolder) TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
        if (holder == null) {
            return;
        }
        Session session = holder.getEntityManager().unwrap(Session.class);
        AppUserPrincipal u = user.get();
        if (u.isAllBranches()) {
            session.enableFilter(BranchScopedEntity.COMPANY_FILTER).setParameter("companyId", u.getCompanyId());
        } else {
            // An empty list would be invalid SQL; -1 matches nothing
            Set<Long> branches = u.getBranchIds().isEmpty() ? Set.of(-1L) : u.getBranchIds();
            session.enableFilter(BranchScopedEntity.BRANCH_FILTER)
                    .setParameter("companyId", u.getCompanyId())
                    .setParameterList("branchIds", branches);
        }
    }
}
