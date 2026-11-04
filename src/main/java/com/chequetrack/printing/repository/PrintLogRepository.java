package com.chequetrack.printing.repository;

import com.chequetrack.printing.domain.PrintLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PrintLogRepository extends JpaRepository<PrintLog, Long> {

    List<PrintLog> findByChequeIdOrderByIdDesc(Long chequeId);

    @Query("select p from PrintLog p where p.chequeId = :chequeId "
            + "and p.outcome = com.chequetrack.printing.domain.PrintLog.Outcome.PENDING order by p.id desc")
    List<PrintLog> findPendingForCheque(@Param("chequeId") Long chequeId);

    @Query("select p from PrintLog p where p.outcome = com.chequetrack.printing.domain.PrintLog.Outcome.PENDING "
            + "order by p.batchRef, p.chequeNumber")
    List<PrintLog> findAllPending();

    @Query("select count(p) > 0 from PrintLog p where p.chequeId = :chequeId "
            + "and p.outcome = com.chequetrack.printing.domain.PrintLog.Outcome.OK")
    boolean printedBefore(@Param("chequeId") Long chequeId);

    Optional<PrintLog> findFirstByBatchRef(String batchRef);

    @Query(value = """
            select p from PrintLog p
             where p.kind in :kinds and p.outcome in :outcomes
               and (cast(:fromTs as timestamp) is null or p.createdAt >= :fromTs)
               and (cast(:toTs as timestamp) is null or p.createdAt < :toTs)
               and (:q = '' or lower(p.chequeNumber) like :q or lower(p.createdBy) like :q)
            """)
    Page<PrintLog> search(@Param("kinds") Collection<PrintLog.Kind> kinds,
                          @Param("outcomes") Collection<PrintLog.Outcome> outcomes,
                          @Param("fromTs") Instant from, @Param("toTs") Instant to,
                          @Param("q") String likeOrEmpty, Pageable pageable);
}
