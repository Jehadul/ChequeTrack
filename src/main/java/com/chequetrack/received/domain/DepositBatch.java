package com.chequetrack.received.domain;

import com.chequetrack.common.domain.Text;
import com.chequetrack.organization.scope.BranchScopedEntity;
import com.chequetrack.setup.domain.CompanyBankAccount;
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

/** Cheques given to the bank together, with one deposit slip. */
@Getter
@Entity
@Table(name = "deposit_batch")
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DepositBatch extends BranchScopedEntity {

    @Column(nullable = false, length = 30, updatable = false)
    private String number;

    @Column(name = "deposit_date", nullable = false, updatable = false)
    private LocalDate depositDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private CompanyBankAccount account;

    @Column(name = "cheque_count", nullable = false)
    private int chequeCount;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(length = 300)
    private String notes;

    public static DepositBatch create(CompanyBankAccount account, String number, LocalDate depositDate, String notes) {
        DepositBatch b = new DepositBatch();
        b.assignScope(account.getCompanyId(), account.getBranchId());
        b.account = account;
        b.number = number;
        b.depositDate = depositDate;
        b.notes = Text.clean(notes);
        return b;
    }

    public void addCheque(BigDecimal amount) {
        chequeCount++;
        totalAmount = totalAmount.add(amount);
    }
}
