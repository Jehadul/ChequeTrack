package com.chequetrack.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;

/**
 * Parent of every entity. Provides the identity key, audit columns filled by
 * JPA auditing, an optimistic-lock version and a soft-delete flag.
 *
 * <p>Matching columns every table must have:
 * <pre>
 *   id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 *   created_at  TIMESTAMPTZ  NOT NULL,
 *   created_by  VARCHAR(100) NOT NULL,
 *   updated_at  TIMESTAMPTZ  NOT NULL,
 *   updated_by  VARCHAR(100) NOT NULL,
 *   version     BIGINT       NOT NULL DEFAULT 0,
 *   deleted     BOOLEAN      NOT NULL DEFAULT FALSE
 * </pre>
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    /** Optimistic lock: a stale update fails instead of silently overwriting another user's change. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Records are never physically removed; see ARCHITECTURE.md §3. */
    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    public void markDeleted() {
        this.deleted = true;
    }

    public boolean isNew() {
        return id == null;
    }

    /**
     * Call with the version the user's form was loaded with. If someone else saved
     * the record since, the edit is refused instead of overwriting their change.
     */
    public void checkVersion(Long formVersion) {
        if (formVersion != null && !formVersion.equals(version)) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(getClass(), id);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || org.hibernate.Hibernate.getClass(this) != org.hibernate.Hibernate.getClass(o)) return false;
        BaseEntity other = (BaseEntity) o;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return org.hibernate.Hibernate.getClass(this).hashCode();
    }
}
