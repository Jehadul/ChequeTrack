package com.chequetrack.printing.repository;

import com.chequetrack.printing.domain.PrinterProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrinterProfileRepository extends JpaRepository<PrinterProfile, Long> {

    List<PrinterProfile> findByCompanyIdOrderByName(Long companyId);

    boolean existsByCompanyIdAndNameIgnoreCaseAndIdNot(Long companyId, String name, Long exceptId);
}
