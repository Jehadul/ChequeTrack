package com.chequetrack.issued.domain;

import com.chequetrack.setup.domain.OpenItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A supplier bill an issued cheque pays, with the planned amount and what was actually
 * settled at hand-over (so a void or stop payment reverses exactly that).
 */
@Getter
@Entity
@Table(name = "issued_cheque_bill")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssuedChequeBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cheque_id", nullable = false, updatable = false)
    private IssuedCheque cheque;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "open_item_id", nullable = false, updatable = false)
    private OpenItem bill;

    @Column(nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "settled_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal settledAmount = BigDecimal.ZERO;

    IssuedChequeBill(IssuedCheque cheque, OpenItem bill, BigDecimal amount) {
        this.cheque = cheque;
        this.bill = bill;
        this.amount = amount;
    }

    /** Cheque handed over: settle the bill by up to the planned amount. */
    void settle() {
        settledAmount = bill.applySettlement(amount);
    }

    /** Cheque voided or stopped: re-open the bill by what was settled. */
    void reverse() {
        if (settledAmount.signum() > 0) {
            bill.reverseSettlement(settledAmount);
            settledAmount = BigDecimal.ZERO;
        }
    }
}
