package com.chequetrack.chequebook.repository;

import com.chequetrack.chequebook.domain.BookStatus;
import com.chequetrack.chequebook.domain.ChequeBook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Branch-scoped: JPQL queries are limited by the branch filter automatically. */
public interface ChequeBookRepository extends JpaRepository<ChequeBook, Long> {

    @Query("select b from ChequeBook b join fetch b.account a join fetch a.bankBranch bb join fetch bb.bank "
            + "where (:accountId is null or a.id = :accountId) and (:status is null or b.status = :status) "
            + "order by bb.bank.name, a.accountNumber, b.firstNumber")
    List<ChequeBook> search(@Param("accountId") Long accountId, @Param("status") BookStatus status);

    /**
     * Does another book of this account and series already cover any of these numbers?
     * Native SQL so books of branches the user cannot see are checked too.
     */
    @Query(value = "select exists(select 1 from cheque_book where account_id = :accountId and prefix = :prefix "
            + "and deleted = false and first_number <= :last and last_number >= :first)", nativeQuery = true)
    boolean rangeOverlaps(@Param("accountId") Long accountId, @Param("prefix") String prefix,
                          @Param("first") long first, @Param("last") long last);
}
