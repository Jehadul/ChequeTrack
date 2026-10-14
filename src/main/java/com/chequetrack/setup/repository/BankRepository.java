package com.chequetrack.setup.repository;

import com.chequetrack.setup.domain.Bank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankRepository extends JpaRepository<Bank, Long> {

    List<Bank> findByCompanyIdOrderByName(Long companyId);

    List<Bank> findByCompanyIdAndActiveTrueOrderByName(Long companyId);

    Optional<Bank> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndCodeIgnoreCase(Long companyId, String code);
}
