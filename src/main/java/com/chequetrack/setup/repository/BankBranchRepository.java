package com.chequetrack.setup.repository;

import com.chequetrack.setup.domain.BankBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public interface BankBranchRepository extends JpaRepository<BankBranch, Long> {

    List<BankBranch> findByBankIdOrderByName(Long bankId);

    @Query("select b from BankBranch b join fetch b.bank where b.companyId = :companyId and b.active = true "
            + "and b.bank.active = true order by b.bank.name, b.name")
    List<BankBranch> findSelectable(@Param("companyId") Long companyId);

    /** With its bank loaded, so pages can show the bank name. */
    @Query("select b from BankBranch b join fetch b.bank where b.id = :id and b.companyId = :companyId")
    Optional<BankBranch> findByIdAndCompanyId(@Param("id") Long id, @Param("companyId") Long companyId);

    Optional<BankBranch> findByCompanyIdAndRoutingNumber(Long companyId, String routingNumber);

    @Query("select b.bank.id, count(b) from BankBranch b where b.companyId = :companyId group by b.bank.id")
    List<Object[]> countPerBank(@Param("companyId") Long companyId);

    default Map<Long, Long> branchCounts(Long companyId) {
        return countPerBank(companyId).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
    }
}
