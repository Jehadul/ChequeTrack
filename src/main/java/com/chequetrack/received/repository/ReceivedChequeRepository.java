package com.chequetrack.received.repository;

import com.chequetrack.lifecycle.domain.ReceivedStatus;
import com.chequetrack.received.domain.ReceivedCheque;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** Branch-scoped: JPQL queries are limited by the branch filter automatically. */
public interface ReceivedChequeRepository extends JpaRepository<ReceivedCheque, Long> {

    @Query(value = """
            select c from ReceivedCheque c join fetch c.party p join fetch c.drawerBank
             where c.status in :statuses
               and (:partyId is null or p.id = :partyId)
               and (cast(:fromDate as date) is null or c.chequeDate >= :fromDate)
               and (cast(:toDate as date) is null or c.chequeDate <= :toDate)
               and (:q = '' or lower(c.chequeNumber) like :q or lower(p.name) like :q)
            """,
            countQuery = """
            select count(c) from ReceivedCheque c join c.party p
             where c.status in :statuses
               and (:partyId is null or p.id = :partyId)
               and (cast(:fromDate as date) is null or c.chequeDate >= :fromDate)
               and (cast(:toDate as date) is null or c.chequeDate <= :toDate)
               and (:q = '' or lower(c.chequeNumber) like :q or lower(p.name) like :q)
            """)
    Page<ReceivedCheque> search(@Param("statuses") Collection<ReceivedStatus> statuses, @Param("partyId") Long partyId,
                                @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
                                @Param("q") String likeOrEmpty, Pageable pageable);

    /** Cheques in hand that may be deposited on the given date (not post-dated beyond it). */
    @Query("""
            select c from ReceivedCheque c join fetch c.party join fetch c.drawerBank join fetch c.drawerBankBranch
             where c.status = com.chequetrack.lifecycle.domain.ReceivedStatus.IN_HAND
               and c.chequeType <> com.chequetrack.received.domain.ChequeType.SECURITY
               and c.chequeDate <= :date
             order by c.chequeDate, c.id
            """)
    List<ReceivedCheque> findDueForDeposit(@Param("date") LocalDate date);

    @Query("select c from ReceivedCheque c join fetch c.party join fetch c.drawerBank join fetch c.drawerBankBranch "
            + "where c.depositBatch.id = :batchId order by c.id")
    List<ReceivedCheque> findByBatch(@Param("batchId") Long batchId);

    /**
     * Same bank, drawer account and cheque number already entered (cancelled entries do not count).
     * Native SQL, so cheques of every branch are checked.
     */
    @Query(value = "select exists(select 1 from received_cheque where company_id = :companyId and drawer_bank_id = :bankId "
            + "and drawer_account_number = :account and cheque_number = :number and deleted = false and status <> 'CANCELLED')",
            nativeQuery = true)
    boolean identityTaken(@Param("companyId") Long companyId, @Param("bankId") Long bankId,
                          @Param("account") String account, @Param("number") String number);
}
