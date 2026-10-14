package com.chequetrack.setup.domain;

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
import org.hibernate.annotations.SQLRestriction;

/** One entry of a reason list, e.g. bounce reason "Insufficient funds". */
@Getter
@Entity
@Table(name = "reason")
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reason extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", nullable = false, length = 20, updatable = false)
    private ReasonType reasonType;

    @Column(nullable = false, length = 20, updatable = false)
    private String code;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;

    public static Reason create(Long companyId, ReasonType type, String code) {
        Reason reason = new Reason();
        reason.companyId = companyId;
        reason.reasonType = type;
        reason.code = Text.code(code);
        return reason;
    }

    public void update(String description, int sortOrder) {
        this.description = description.trim();
        this.sortOrder = sortOrder;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
