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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/** One audit log row. Append-only: the database refuses updates and deletes. */
@Getter
@Entity
@Immutable
@Table(name = "audit_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id", length = 50)
    private String entityId;

    @Column(nullable = false, length = 500)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Builder
    private AuditLogEntry(Instant occurredAt, Long companyId, Long userId, String username, String action,
                          String entityType, String entityId, String summary, Map<String, Object> details,
                          String ipAddress) {
        this.occurredAt = occurredAt;
        this.companyId = companyId;
        this.userId = userId;
        this.username = username;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.summary = summary.length() > 500 ? summary.substring(0, 497) + "..." : summary;
        this.details = details;
        this.ipAddress = ipAddress;
    }
}
