package com.chequetrack.issued.repository;

import com.chequetrack.issued.domain.IssuedCheque;
import com.chequetrack.lifecycle.domain.IssuedStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** Branch-scoped: JPQL queries are limited by the branch filter automatically. */
public interface IssuedChequeRepository extends JpaRepository<IssuedCheque, Long> {

    @Query(value = """
            select c from IssuedCheque c join fetch c.party p join fetch c.account a join fetch a.bankBranch bb join fetch bb.bank
             where c.status in :statuses
               and (:partyId is null or p.id = :partyId)
               and (:accountId is null or a.id = :accountId)
               and (cast(:fromDate as date) is null or c.chequeDate >= :fromDate)
               and (cast(:toDate as date) is null or c.chequeDate <= :toDate)
               and (:q = '' or lower(c.chequeNumber) like :q or lower(c.payeeName) like :q or lower(p.name) like :q)
            """,
            countQuery = """
            select count(c) from IssuedCheque c join c.party p join c.account a
             where c.status in :statuses
               and (:partyId is null or p.id = :partyId)
               and (:accountId is null or a.id = :accountId)
               and (cast(:fromDate as date) is null or c.chequeDate >= :fromDate)
               and (cast(:toDate as date) is null or c.chequeDate <= :toDate)
               and (:q = '' or lower(c.chequeNumber) like :q or lower(c.payeeName) like :q or lower(p.name) like :q)
            """)
    Page<IssuedCheque> search(@Param("statuses") Collection<IssuedStatus> statuses, @Param("partyId") Long partyId,
                              @Param("accountId") Long accountId, @Param("fromDate") LocalDate fromDate,
                              @Param("toDate") LocalDate toDate, @Param("q") String likeOrEmpty, Pageable pageable);

    /** Supplier PDC register: cheques with the supplier and not yet paid by the bank, by due date. */
    @Query("""
            select c from IssuedCheque c join fetch c.party p join fetch c.account a join fetch a.bankBranch bb join fetch bb.bank
              left join fetch c.payment
             where c.status = com.chequetrack.lifecycle.domain.IssuedStatus.ISSUED
               and (:partyId is null or p.id = :partyId)
             order by c.chequeDate, c.id
            """)
    List<IssuedCheque> findOutstanding(@Param("partyId") Long partyId);

    /** Has this void or stopped cheque already been written again (cancelled reissues do not count)? */
    @Query(value = "select exists(select 1 from issued_cheque where reissue_of_id = :id and deleted = false and status <> 'CANCELLED')",
            nativeQuery = true)
    boolean alreadyReissued(@Param("id") Long id);

    /** The live reissue of a cheque, if any. */
    @Query("select c from IssuedCheque c where c.reissueOfId = :id "
            + "and c.status <> com.chequetrack.lifecycle.domain.IssuedStatus.CANCELLED")
    List<IssuedCheque> findReissues(@Param("id") Long id);
}
