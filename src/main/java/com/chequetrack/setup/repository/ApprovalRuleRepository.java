package com.chequetrack.setup.repository;

import com.chequetrack.setup.domain.ApprovalAction;
import com.chequetrack.setup.domain.ApprovalRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovalRuleRepository extends JpaRepository<ApprovalRule, Long> {

    List<ApprovalRule> findByCompanyIdOrderByActionAscStepOrderAsc(Long companyId);

    List<ApprovalRule> findByCompanyIdAndActionOrderByStepOrderAsc(Long companyId, ApprovalAction action);

    Optional<ApprovalRule> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndActionAndStepOrder(Long companyId, ApprovalAction action, int stepOrder);
}
