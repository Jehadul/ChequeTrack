package com.chequetrack.audit.repository;

import com.chequetrack.audit.domain.AuditLogEntry;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long>, JpaSpecificationExecutor<AuditLogEntry> {

    List<AuditLogEntry> findByCompanyIdAndEntityTypeAndEntityIdOrderByOccurredAtDesc(
            Long companyId, String entityType, String entityId, Limit limit);

    @Query("select distinct a.action from AuditLogEntry a where a.companyId = :companyId or a.companyId is null order by a.action")
    List<String> findActions(@Param("companyId") Long companyId);

    @Query("select distinct a.entityType from AuditLogEntry a where a.entityType is not null and a.companyId = :companyId order by a.entityType")
    List<String> findEntityTypes(@Param("companyId") Long companyId);
}
