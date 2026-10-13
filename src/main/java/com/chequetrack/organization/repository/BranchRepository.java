package com.chequetrack.organization.repository;

import com.chequetrack.organization.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Every lookup includes the company id, so one company can never read another's branches. */
public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findByCompanyIdOrderByCode(Long companyId);

    List<Branch> findByCompanyIdAndIdInOrderByCode(Long companyId, Collection<Long> ids);

    List<Branch> findByCompanyIdAndActiveTrueOrderByCode(Long companyId);

    Optional<Branch> findByIdAndCompanyId(Long id, Long companyId);

    Optional<Branch> findByCompanyIdAndCodeIgnoreCase(Long companyId, String code);

    boolean existsByCompanyIdAndCodeIgnoreCase(Long companyId, String code);
}
