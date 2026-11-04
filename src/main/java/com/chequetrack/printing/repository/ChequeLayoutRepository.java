package com.chequetrack.printing.repository;

import com.chequetrack.printing.domain.ChequeLayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChequeLayoutRepository extends JpaRepository<ChequeLayout, Long> {

    @Query("select l from ChequeLayout l join fetch l.account a join fetch a.bankBranch bb join fetch bb.bank "
            + "order by bb.bank.name, a.accountNumber, l.active desc, l.name")
    List<ChequeLayout> findAllVisible();

    @Query("select l from ChequeLayout l where l.account.id = :accountId and l.active = true")
    Optional<ChequeLayout> findActive(@Param("accountId") Long accountId);

    @Query("select l from ChequeLayout l where l.account.id = :accountId and l.active = true and l.id <> :exceptId")
    List<ChequeLayout> findOtherActive(@Param("accountId") Long accountId, @Param("exceptId") Long exceptId);
}
