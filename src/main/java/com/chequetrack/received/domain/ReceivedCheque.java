package com.chequetrack.received.domain;

import com.chequetrack.common.domain.Text;
import com.chequetrack.lifecycle.domain.ReceivedStatus;
import com.chequetrack.lifecycle.service.LifecycleRecord;
import com.chequetrack.organization.scope.BranchScopedEntity;
import com.chequetrack.setup.domain.Bank;
import com.chequetrack.setup.domain.BankBranch;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A cheque received from a customer. Its status only changes through the lifecycle
 * engine (TransitionService), which calls the methods below from
 * ReceivedChequeLifecycle inside one transaction.
 */
@Getter
@Entity
@Table(name = "received_cheque")
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceivedCheque extends BranchScopedEntity implements LifecycleRecord {

    public static final String RECORD_TYPE = "ReceivedCheque";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false, updatable = false)
    private Party party;

    @Enumerated(EnumType.STRING)
    @Column(name = "cheque_type", nullable = false, length = 20)
    private ChequeType chequeType;

    @Column(name = "cheque_number", nullable = false, length = 20, updatable = false)
    private String chequeNumber;

    @Column(name = "cheque_date", nullable = false)
    private LocalDate chequeDate;

    @Column(name = "original_date", nullable = false, updatable = false)
    private LocalDate originalDate;

    @Column(nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drawer_bank_id", nullable = false, updatable = false)
    private Bank drawerBank;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drawer_bank_branch_id", nullable = false, updatable = false)
    private BankBranch drawerBankBranch;

    @Column(name = "drawer_account_number", nullable = false, length = 30, updatable = false)
    private String drawerAccountNumber;

    @Column(name = "drawer_name", length = 150)
    private String drawerName;

    @Column(name = "received_date", nullable = false, updatable = false)
    private LocalDate receivedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReceivedStatus status = ReceivedStatus.IN_HAND;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deposit_batch_id")
    private DepositBatch depositBatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id")
    private Receipt receipt;

    @Column(name = "cleared_date")
    private LocalDate clearedDate;

    @Column(name = "defer_count", nullable = false)
    private int deferCount;

    @Column(name = "represent_count", nullable = false)
    private int representCount;

    @Column(name = "bounce_count", nullable = false)
    private int bounceCount;

    @Column(name = "bank_charges", nullable = false, precision = 18, scale = 2)
    private BigDecimal bankCharges = BigDecimal.ZERO;

    @Column(name = "last_bounce_reason", length = 500)
    private String lastBounceReason;

    @Column(name = "last_bounce_date")
    private LocalDate lastBounceDate;

    @Column(name = "replaces_id", updatable = false)
    private Long replacesId;

    @Column(length = 300)
    private String narration;

    @OneToMany(mappedBy = "cheque", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReceivedChequeInvoice> invoices = new ArrayList<>();

    public static ReceivedCheque create(Long companyId, Long branchId, Party party, ChequeType type, String number,
                                        LocalDate chequeDate, BigDecimal amount, BankBranch drawerBankBranch,
                                        String drawerAccountNumber, String drawerName, LocalDate receivedDate,
                                        String narration, Long replacesId) {
        ReceivedCheque c = new ReceivedCheque();
        c.assignScope(companyId, branchId);
        c.party = party;
        c.chequeType = type;
        c.chequeNumber = number.trim();
        c.chequeDate = chequeDate;
        c.originalDate = chequeDate;
        c.amount = amount;
        c.drawerBank = drawerBankBranch.getBank();
        c.drawerBankBranch = drawerBankBranch;
        c.drawerAccountNumber = drawerAccountNumber.trim();
        c.drawerName = Text.clean(drawerName);
        c.receivedDate = receivedDate;
        c.narration = Text.clean(narration);
        c.replacesId = replacesId;
        return c;
    }

    public void linkInvoice(OpenItem invoice, BigDecimal amount) {
        invoices.add(new ReceivedChequeInvoice(this, invoice, amount));
    }

    // ---- LifecycleRecord ------------------------------------------------------------------------

    @Override
    public String lifecycleStatus() {
        return status.name();
    }

    @Override
    public void applyLifecycleStatus(String newStatus) {
        this.status = ReceivedStatus.valueOf(newStatus);
    }

    @Override
    public BigDecimal approvalAmount() {
        return amount;
    }

    @Override
    public String lifecycleLabel() {
        return "Cheque " + chequeNumber + " · " + party.getName();
    }

    // ---- changes: called only by ReceivedChequeLifecycle (inside a lifecycle transition) ---------

    public void deposited(DepositBatch batch) {
        this.depositBatch = batch;
    }

    public void recalled() {
        this.depositBatch = null;
    }

    public void cleared(Receipt receipt, LocalDate date) {
        this.receipt = receipt;
        this.clearedDate = date;
        invoices.forEach(ReceivedChequeInvoice::settle);
    }

    public void bounced(String reason, LocalDate date, BigDecimal charge) {
        this.bounceCount++;
        this.lastBounceReason = reason;
        this.lastBounceDate = date;
        this.bankCharges = bankCharges.add(charge == null ? BigDecimal.ZERO : charge);
        if (receipt != null && receipt.isActive()) {
            receipt.cancel("Cheque bounced: " + reason);
        }
        invoices.forEach(ReceivedChequeInvoice::reverse);
        this.clearedDate = null;
        this.depositBatch = null;
    }

    public void represented() {
        this.representCount++;
    }

    public void deferred(LocalDate newDate) {
        this.deferCount++;
        this.chequeDate = newDate;
    }

    // ---- derived values for pages ---------------------------------------------------------------

    /** Last day the bank accepts the cheque (written date + validity days). */
    public LocalDate expiryDate(int validityDays) {
        return originalDate.plusDays(validityDays);
    }

    public boolean isAwaitingBank() {
        return status == ReceivedStatus.IN_HAND || status == ReceivedStatus.BOUNCED;
    }
}
