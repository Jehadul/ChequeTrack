package com.chequetrack.organization.repository;

import com.chequetrack.organization.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    @Query("select c from Company c where upper(c.code) = upper(:code)")
    Optional<Company> findByCode(@Param("code") String code);
}
