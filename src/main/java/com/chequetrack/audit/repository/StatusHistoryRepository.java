package com.chequetrack.audit.repository;

import com.chequetrack.audit.domain.StatusHistoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatusHistoryRepository extends JpaRepository<StatusHistoryEntry, Long> {

    List<StatusHistoryEntry> findByCompanyIdAndEntityTypeAndEntityIdOrderByChangedAtAscIdAsc(
            Long companyId, String entityType, Long entityId);
}
