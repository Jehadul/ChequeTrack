package com.chequetrack.notification.repository;

import com.chequetrack.notification.domain.OutboxEmail;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxEmailRepository extends JpaRepository<OutboxEmail, Long> {

    List<OutboxEmail> findByCompanyIdOrderByIdDesc(Long companyId, Limit limit);

    @Query("select e from OutboxEmail e where e.status = com.chequetrack.notification.domain.OutboxEmail.Status.PENDING order by e.id")
    List<OutboxEmail> findPending(Limit limit);

    @Query("select count(e) > 0 from OutboxEmail e where lower(e.toAddress) = lower(:to) and e.dedupKey = :key")
    boolean alreadyQueued(@Param("to") String toAddress, @Param("key") String dedupKey);
}
