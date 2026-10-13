package com.chequetrack.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** One status change of any record. Append-only: the database refuses updates and deletes. */
@Getter
@Entity
@Immutable
@Table(name = "status_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StatusHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 30)
    private String toStatus;

    @Column(length = 500)
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 100)
    private String username;

    @Builder
    private StatusHistoryEntry(Long companyId, Long branchId, String entityType, Long entityId, String fromStatus,
                               String toStatus, String reason, Instant changedAt, Long userId, String username) {
        this.companyId = companyId;
        this.branchId = branchId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.changedAt = changedAt;
        this.userId = userId;
        this.username = username;
    }
}
