package com.chequetrack.setup.repository;

import com.chequetrack.setup.domain.CompanyBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Branch-scoped: list queries are limited by the branch filter automatically,
 * so they need no company or branch condition of their own.
 */
public interface CompanyBankAccountRepository extends JpaRepository<CompanyBankAccount, Long> {

    @Query("select a from CompanyBankAccount a join fetch a.bankBranch bb join fetch bb.bank "
            + "order by bb.bank.name, a.accountNumber")
    List<CompanyBankAccount> findAllVisible();

    /**
     * Duplicate check across the whole company. Native SQL on purpose: the branch
     * filter would hide accounts of other branches and the check would miss them.
     */
    @Query(value = "select exists(select 1 from company_bank_account where company_id = :companyId "
            + "and bank_branch_id = :bankBranchId and account_number = :accountNumber and deleted = false "
            + "and id <> coalesce(:excludeId, -1))", nativeQuery = true)
    boolean numberTaken(@Param("companyId") Long companyId, @Param("bankBranchId") Long bankBranchId,
                        @Param("accountNumber") String accountNumber, @Param("excludeId") Long excludeId);

    /** Company-wide (native, unfiltered): is this bank branch used by any account? */
    @Query(value = "select count(*) from company_bank_account where bank_branch_id = :bankBranchId and deleted = false",
            nativeQuery = true)
    long countUsingBankBranch(@Param("bankBranchId") Long bankBranchId);
}
