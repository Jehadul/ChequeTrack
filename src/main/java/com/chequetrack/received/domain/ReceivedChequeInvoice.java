package com.chequetrack.received.domain;

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
 * An invoice a received cheque pays, with the planned amount and what was actually
 * settled when the cheque cleared (so a later bounce reverses exactly that).
 */
@Getter
@Entity
@Table(name = "received_cheque_invoice")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceivedChequeInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cheque_id", nullable = false, updatable = false)
    private ReceivedCheque cheque;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "open_item_id", nullable = false, updatable = false)
    private OpenItem invoice;

    @Column(nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "settled_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal settledAmount = BigDecimal.ZERO;

    ReceivedChequeInvoice(ReceivedCheque cheque, OpenItem invoice, BigDecimal amount) {
        this.cheque = cheque;
        this.invoice = invoice;
        this.amount = amount;
    }

    /** Cheque cleared: settle the invoice by up to the planned amount. */
    void settle() {
        settledAmount = invoice.applySettlement(amount);
    }

    /** Cheque bounced after clearing: re-open the invoice by what was settled. */
    void reverse() {
        if (settledAmount.signum() > 0) {
            invoice.reverseSettlement(settledAmount);
            settledAmount = BigDecimal.ZERO;
        }
    }
}
