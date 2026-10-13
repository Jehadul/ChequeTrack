package com.chequetrack.organization.repository;

import com.chequetrack.organization.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    List<Role> findByCompanyIdOrderByName(Long companyId);

    Optional<Role> findByIdAndCompanyId(Long id, Long companyId);

    Optional<Role> findByCompanyIdAndCodeIgnoreCase(Long companyId, String code);

    boolean existsByCompanyIdAndCodeIgnoreCase(Long companyId, String code);
}
