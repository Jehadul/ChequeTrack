package com.chequetrack.setup.repository;

import com.chequetrack.setup.domain.Reason;
import com.chequetrack.setup.domain.ReasonType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReasonRepository extends JpaRepository<Reason, Long> {

    List<Reason> findByCompanyIdOrderByReasonTypeAscSortOrderAscDescriptionAsc(Long companyId);

    List<Reason> findByCompanyIdAndReasonTypeAndActiveTrueOrderBySortOrderAscDescriptionAsc(Long companyId, ReasonType type);

    Optional<Reason> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndReasonTypeAndCodeIgnoreCase(Long companyId, ReasonType type, String code);
}
