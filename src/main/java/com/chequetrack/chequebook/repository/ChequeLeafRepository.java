package com.chequetrack.chequebook.repository;

import com.chequetrack.chequebook.domain.ChequeLeaf;
import com.chequetrack.chequebook.domain.LeafStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Branch-scoped: JPQL queries are limited by the branch filter automatically. */
public interface ChequeLeafRepository extends JpaRepository<ChequeLeaf, Long> {

    /**
     * Creates every leaf of a new book in one statement. The unique index on
     * (account_id, prefix, leaf_number) refuses any number that already exists.
     */
    @Modifying
    @Query(value = """
            INSERT INTO cheque_leaf (company_id, branch_id, book_id, account_id, prefix, leaf_number, status,
                                     created_at, created_by, updated_at, updated_by, version, deleted)
            SELECT b.company_id, b.branch_id, b.id, b.account_id, b.prefix, n, 'UNUSED',
                   :now, :user, :now, :user, 0, FALSE
              FROM cheque_book b, generate_series(b.first_number, b.last_number) AS n
             WHERE b.id = :bookId
            """, nativeQuery = true)
    int createLeaves(@Param("bookId") Long bookId, @Param("now") Instant now, @Param("user") String user);

    /**
     * Locks the next unused leaf of an account (oldest active book first, lowest number) for the
     * calling transaction. SKIP LOCKED makes a second user who asks at the same moment get the
     * following leaf instead of waiting, so two cheques never share a number.
     */
    @Query(value = """
            SELECT l.* FROM cheque_leaf l JOIN cheque_book b ON b.id = l.book_id
             WHERE l.account_id = :accountId AND l.status = 'UNUSED' AND l.deleted = FALSE
               AND b.status = 'ACTIVE' AND b.deleted = FALSE
             ORDER BY b.received_date, b.id, l.leaf_number
             LIMIT 1
               FOR UPDATE OF l SKIP LOCKED
            """, nativeQuery = true)
    Optional<ChequeLeaf> lockNextUnused(@Param("accountId") Long accountId);

    /** The leaf the next cheque would get (not locked; shown on the form only). */
    @Query(value = """
            SELECT l.* FROM cheque_leaf l JOIN cheque_book b ON b.id = l.book_id
             WHERE l.account_id = :accountId AND l.status = 'UNUSED' AND l.deleted = FALSE
               AND b.status = 'ACTIVE' AND b.deleted = FALSE
             ORDER BY b.received_date, b.id, l.leaf_number
             LIMIT 1
            """, nativeQuery = true)
    Optional<ChequeLeaf> peekNextUnused(@Param("accountId") Long accountId);

    @Query("select l from ChequeLeaf l where l.book.id = :bookId order by l.leafNumber")
    List<ChequeLeaf> findByBook(@Param("bookId") Long bookId);

    /** Rows of [bookId, status, count] for the given books. */
    @Query("select l.book.id, l.status, count(l) from ChequeLeaf l where l.book.id in :bookIds group by l.book.id, l.status")
    List<Object[]> countByBookAndStatus(@Param("bookIds") Collection<Long> bookIds);

    /** Rows of [accountId, status, count] for leaves of active books the user may see. */
    @Query("select l.accountId, l.status, count(l) from ChequeLeaf l where l.book.status = com.chequetrack.chequebook.domain.BookStatus.ACTIVE "
            + "group by l.accountId, l.status")
    List<Object[]> countActiveByAccountAndStatus();

    @Query("select count(l) from ChequeLeaf l where l.book.id = :bookId and l.status in :statuses")
    long countByBookAndStatusIn(@Param("bookId") Long bookId, @Param("statuses") Collection<LeafStatus> statuses);

    /**
     * Skipped leaves: still unused although a later leaf of the same book was
     * already used or voided. A skipped leaf may be lost or used without a record.
     */
    @Query("""
            select l from ChequeLeaf l join fetch l.book b
             where l.status = com.chequetrack.chequebook.domain.LeafStatus.UNUSED
               and l.leafNumber < (select max(l2.leafNumber) from ChequeLeaf l2
                                    where l2.book = l.book
                                      and l2.status in (com.chequetrack.chequebook.domain.LeafStatus.USED,
                                                        com.chequetrack.chequebook.domain.LeafStatus.VOID))
               and (:bookId is null or b.id = :bookId)
             order by b.id, l.leafNumber
            """)
    List<ChequeLeaf> findSkipped(@Param("bookId") Long bookId);

    /** Closes every unused leaf of a book that is cancelled or lost. */
    @Modifying
    @Query("update ChequeLeaf l set l.status = :newStatus, l.updatedAt = :now, l.updatedBy = :user "
            + "where l.book.id = :bookId and l.status = com.chequetrack.chequebook.domain.LeafStatus.UNUSED")
    int closeUnused(@Param("bookId") Long bookId, @Param("newStatus") LeafStatus newStatus,
                    @Param("now") Instant now, @Param("user") String user);
}
