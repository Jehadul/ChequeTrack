package com.chequetrack.attachment.domain;

import com.chequetrack.common.domain.BaseEntity;
import com.chequetrack.common.domain.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/**
 * Metadata of an uploaded file linked to a record ({@code ownerType} + {@code ownerId}).
 * The bytes live on disk under {@code storageKey}. Removing an attachment only
 * marks the row deleted; the file is kept.
 */
@Getter
@Entity
@Table(name = "attachment")
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attachment extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "branch_id", updatable = false)
    private Long branchId;

    @Column(name = "owner_type", nullable = false, length = 50, updatable = false)
    private String ownerType;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttachmentCategory category;

    @Column(name = "original_filename", nullable = false, length = 255, updatable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    /** Hex SHA-256 of the file, to detect damaged or swapped files. Always 64 characters, hence CHAR(64). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64, updatable = false)
    private String sha256;

    @Column(name = "storage_key", nullable = false, length = 200, updatable = false)
    private String storageKey;

    @Column(length = 300)
    private String description;

    public static Attachment create(Long companyId, Long branchId, String ownerType, Long ownerId,
                                    AttachmentCategory category, String originalFilename, String contentType,
                                    long sizeBytes, String sha256, String storageKey, String description) {
        Attachment a = new Attachment();
        a.companyId = companyId;
        a.branchId = branchId;
        a.ownerType = ownerType;
        a.ownerId = ownerId;
        a.category = category;
        a.originalFilename = originalFilename;
        a.contentType = contentType;
        a.sizeBytes = sizeBytes;
        a.sha256 = sha256;
        a.storageKey = storageKey;
        a.description = Text.clean(description);
        return a;
    }

    public boolean isPreviewable() {
        return contentType.startsWith("image/") || contentType.equals("application/pdf");
    }

    /** "245 KB" */
    public String getSizeLabel() {
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        }
        if (sizeBytes < 1024 * 1024) {
            return (sizeBytes / 1024) + " KB";
        }
        return String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
    }
}
