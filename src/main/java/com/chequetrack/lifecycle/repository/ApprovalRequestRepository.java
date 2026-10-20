package com.chequetrack.lifecycle.repository;

import com.chequetrack.lifecycle.domain.ApprovalRequest;
import com.chequetrack.lifecycle.domain.ApprovalStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Branch-scoped: JPQL queries are limited by the branch filter automatically. */
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    /** Requests whose waiting step needs this role, not made by this user. */
    @Query("""
            select distinct r from ApprovalRequest r join r.steps s
             where r.status = com.chequetrack.lifecycle.domain.ApprovalStatus.PENDING
               and s.status = com.chequetrack.lifecycle.domain.StepStatus.WAITING
               and s.stepOrder = r.currentStep
               and s.roleId = :roleId
               and r.requestedById <> :userId
             order by r.requestedAt
            """)
    List<ApprovalRequest> findInbox(@Param("roleId") Long roleId, @Param("userId") Long userId);

    @Query("""
            select count(distinct r) from ApprovalRequest r join r.steps s
             where r.status = com.chequetrack.lifecycle.domain.ApprovalStatus.PENDING
               and s.status = com.chequetrack.lifecycle.domain.StepStatus.WAITING
               and s.stepOrder = r.currentStep
               and s.roleId = :roleId
               and r.requestedById <> :userId
            """)
    long countInbox(@Param("roleId") Long roleId, @Param("userId") Long userId);

    List<ApprovalRequest> findByRequestedByIdOrderByRequestedAtDesc(Long userId, Limit limit);

    Optional<ApprovalRequest> findByRecordTypeAndRecordIdAndStatus(String recordType, Long recordId, ApprovalStatus status);

    List<ApprovalRequest> findByRecordTypeAndRecordIdOrderByRequestedAtDesc(String recordType, Long recordId);
}
