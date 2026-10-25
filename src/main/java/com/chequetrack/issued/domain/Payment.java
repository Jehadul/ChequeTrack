package com.chequetrack.issued.domain;

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

/**
 * Supplier payment created when an issued cheque is handed over, dated on the cheque's
 * due date. Cancelled (never deleted) if the cheque is voided or stopped.
 */
@Getter
@Entity
@Table(name = "payment")
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BranchScopedEntity {

    public static final String ACTIVE = "ACTIVE";
    public static final String CANCELLED = "CANCELLED";

    @Column(nullable = false, length = 30, updatable = false)
    private String number;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false, updatable = false)
    private Party party;

    @Column(name = "cheque_id", nullable = false, updatable = false)
    private Long chequeId;

    @Column(nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String status = ACTIVE;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    public static Payment create(IssuedCheque cheque, String number) {
        Payment p = new Payment();
        p.assignScope(cheque.getCompanyId(), cheque.getBranchId());
        p.number = number;
        p.paymentDate = cheque.getChequeDate();
        p.party = cheque.getParty();
        p.chequeId = cheque.getId();
        p.amount = cheque.getAmount();
        return p;
    }

    /** The cheque was deferred: the money leaves on the new date. */
    public void reschedule(LocalDate newDate) {
        this.paymentDate = newDate;
    }

    public void cancel(String reason) {
        if (CANCELLED.equals(status)) {
            throw new BusinessException("Payment " + number + " is already cancelled.");
        }
        this.status = CANCELLED;
        this.cancelReason = reason;
    }

    public boolean isActive() {
        return ACTIVE.equals(status);
    }
}
