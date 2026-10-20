package com.chequetrack.received.repository;

import com.chequetrack.received.domain.DepositBatch;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Branch-scoped: JPQL queries are limited by the branch filter automatically. */
public interface DepositBatchRepository extends JpaRepository<DepositBatch, Long> {

    @Query("select b from DepositBatch b join fetch b.account a join fetch a.bankBranch bb join fetch bb.bank "
            + "order by b.depositDate desc, b.id desc")
    List<DepositBatch> findRecent(Limit limit);

    @Query("select b from DepositBatch b join fetch b.account a join fetch a.bankBranch bb join fetch bb.bank where b.id = :id")
    java.util.Optional<DepositBatch> findWithAccount(@Param("id") Long id);
}
