package com.chequetrack.issued.domain;

import com.chequetrack.chequebook.domain.ChequeLeaf;
import com.chequetrack.common.domain.AmountInWords;
import com.chequetrack.common.domain.Text;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.lifecycle.domain.IssuedStatus;
import com.chequetrack.lifecycle.service.LifecycleRecord;
import com.chequetrack.organization.scope.BranchScopedEntity;
import com.chequetrack.setup.domain.CompanyBankAccount;
import com.chequetrack.setup.domain.OpenItem;
import com.chequetrack.setup.domain.Party;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A cheque the company writes on one of its leaves. Details can be edited while it is a
 * draft; afterwards its status only changes through the lifecycle engine, which calls
 * the change methods below from IssuedChequeLifecycle inside one transaction.
 */
@Getter
@Entity
@Table(name = "issued_cheque")
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssuedCheque extends BranchScopedEntity implements LifecycleRecord {

    public static final String RECORD_TYPE = "IssuedCheque";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private CompanyBankAccount account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leaf_id", nullable = false, updatable = false)
    private ChequeLeaf leaf;

    @Column(name = "cheque_number", nullable = false, length = 30, updatable = false)
    private String chequeNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @Column(name = "payee_name", nullable = false, length = 150)
    private String payeeName;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "amount_in_words", nullable = false, length = 400)
    private String amountInWords;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Crossing crossing;

    @Column(name = "cheque_date", nullable = false)
    private LocalDate chequeDate;

    @Column(name = "original_date", nullable = false)
    private LocalDate originalDate;

    @Column(length = 300)
    private String narration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssuedStatus status = IssuedStatus.DRAFT;

    @Column(name = "print_count", nullable = false)
    private int printCount;

    @Column(name = "printed_at")
    private Instant printedAt;

    @Column(name = "handed_over_to", length = 150)
    private String handedOverTo;

    @Column(name = "handed_over_on")
    private LocalDate handedOverOn;

    @Column(name = "handover_note", length = 200)
    private String handoverNote;

    @Column(name = "cleared_date")
    private LocalDate clearedDate;

    @Column(name = "defer_count", nullable = false)
    private int deferCount;

    @Column(name = "reissue_of_id", updatable = false)
    private Long reissueOfId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @OneToMany(mappedBy = "cheque", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IssuedChequeBill> bills = new ArrayList<>();

    /** A new draft on an allocated leaf. */
    public static IssuedCheque draft(CompanyBankAccount account, ChequeLeaf leaf, Long reissueOfId) {
        IssuedCheque c = new IssuedCheque();
        c.assignScope(account.getCompanyId(), account.getBranchId());
        c.account = account;
        c.leaf = leaf;
        c.chequeNumber = leaf.getDisplayNumber();
        c.reissueOfId = reissueOfId;
        return c;
    }

    /** Sets what is written on the cheque. Only while it is a draft. */
    public void details(Party party, String payeeName, BigDecimal amount, Crossing crossing, LocalDate chequeDate,
                        String narration) {
        if (status != IssuedStatus.DRAFT) {
            throw new BusinessException("Only a draft cheque can be changed. This cheque is " + status.getLabel().toLowerCase() + ".");
        }
        this.party = party;
        this.payeeName = payeeName == null || payeeName.isBlank() ? party.getName() : payeeName.trim();
        this.amount = amount;
        this.amountInWords = AmountInWords.taka(amount);
        this.crossing = crossing;
        this.chequeDate = chequeDate;
        this.originalDate = chequeDate;
        this.narration = Text.clean(narration);
    }

    /** Removes the bill links before they are entered again (flush before re-adding the same bill). */
    public void clearBills() {
        bills.clear();
    }

    public void linkBill(OpenItem bill, BigDecimal amount) {
        bills.add(new IssuedChequeBill(this, bill, amount));
    }

    // ---- LifecycleRecord ------------------------------------------------------------------------

    @Override
    public String lifecycleStatus() {
        return status.name();
    }

    @Override
    public void applyLifecycleStatus(String newStatus) {
        this.status = IssuedStatus.valueOf(newStatus);
    }

    @Override
    public BigDecimal approvalAmount() {
        return amount;
    }

    @Override
    public String lifecycleLabel() {
        return "Cheque " + chequeNumber + " · " + payeeName;
    }

    // ---- changes: called only by IssuedChequeLifecycle (inside a lifecycle transition) -----------

    public void printed(Instant at) {
        this.printCount++;
        this.printedAt = at;
        leaf.markUsed();
    }

    public void handedOver(String collectedBy, LocalDate on, String note, Payment payment) {
        this.handedOverTo = collectedBy.trim();
        this.handedOverOn = on;
        this.handoverNote = Text.clean(note);
        this.payment = payment;
        bills.forEach(IssuedChequeBill::settle);
    }

    public void deferred(LocalDate newDate) {
        this.deferCount++;
        this.chequeDate = newDate;
        if (payment != null) {
            payment.reschedule(newDate);
        }
    }

    public void cleared(LocalDate date) {
        this.clearedDate = date;
    }

    /** Cancelled before printing: the blank leaf goes back to stock. */
    public void cancelled() {
        leaf.release();
    }

    /** Voided after printing: the leaf is void, the payment cancelled and the bills re-opened. */
    public void voided(String reason) {
        leaf.voidWithCheque();
        reversePayment("Cheque voided: " + reason);
    }

    /** Payment stopped at the bank: the payment is cancelled and the bills re-opened (the leaf stays used). */
    public void stopped(String reason) {
        reversePayment("Payment stopped: " + reason);
    }

    private void reversePayment(String why) {
        if (payment != null && payment.isActive()) {
            payment.cancel(why);
        }
        bills.forEach(IssuedChequeBill::reverse);
    }

    // ---- derived values for pages ---------------------------------------------------------------

    /** Last day the bank accepts the cheque (written date + validity days). */
    public LocalDate expiryDate(int validityDays) {
        return originalDate.plusDays(validityDays);
    }

    /** Void or stopped cheques may be written again on a new leaf. */
    public boolean isReissuable() {
        return status == IssuedStatus.VOID || status == IssuedStatus.STOPPED;
    }

    public BigDecimal getBillTotal() {
        return bills.stream().map(IssuedChequeBill::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
