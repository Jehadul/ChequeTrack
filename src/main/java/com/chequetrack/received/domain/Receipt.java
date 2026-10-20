package com.chequetrack.received.domain;

import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.organization.scope.BranchScopedEntity;
import com.chequetrack.setup.domain.Party;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Customer receipt created when a received cheque clears. Cancelled (never deleted) if the cheque bounces later. */
@Getter
@Entity
@Table(name = "receipt")
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Receipt extends BranchScopedEntity {

    public static final String ACTIVE = "ACTIVE";
    public static final String CANCELLED = "CANCELLED";

    @Column(nullable = false, length = 30, updatable = false)
    private String number;

    @Column(name = "receipt_date", nullable = false, updatable = false)
    private LocalDate receiptDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false, updatable = false)
    private Party party;

    @Column(name = "cheque_id", updatable = false)
    private Long chequeId;

    @Column(nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String status = ACTIVE;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    public static Receipt create(ReceivedCheque cheque, String number, LocalDate date) {
        Receipt r = new Receipt();
        r.assignScope(cheque.getCompanyId(), cheque.getBranchId());
        r.number = number;
        r.receiptDate = date;
        r.party = cheque.getParty();
        r.chequeId = cheque.getId();
        r.amount = cheque.getAmount();
        return r;
    }

    public void cancel(String reason) {
        if (CANCELLED.equals(status)) {
            throw new BusinessException("Receipt " + number + " is already cancelled.");
        }
        this.status = CANCELLED;
        this.cancelReason = reason;
    }

    public boolean isActive() {
        return ACTIVE.equals(status);
    }
}
