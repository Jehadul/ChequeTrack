package com.chequetrack.chequebook.domain;

import com.chequetrack.common.domain.Text;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.organization.scope.BranchScopedEntity;
import com.chequetrack.setup.domain.CompanyBankAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

/**
 * A cheque book of a company bank account: a continuous range of leaf numbers.
 * Its leaves are created together with it ({@link ChequeLeaf}). Branch-scoped,
 * with the branch of the account.
 */
@Getter
@Entity
@Table(name = "cheque_book")
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChequeBook extends BranchScopedEntity {

    /** Largest book accepted (the database allows up to 1000 leaves). */
    public static final int MAX_LEAVES = 500;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private CompanyBankAccount account;

    @Column(nullable = false, length = 10, updatable = false)
    private String prefix;

    @Column(name = "first_number", nullable = false, updatable = false)
    private long firstNumber;

    @Column(name = "last_number", nullable = false, updatable = false)
    private long lastNumber;

    @Column(name = "number_width", nullable = false, updatable = false)
    private int numberWidth;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookStatus status = BookStatus.ACTIVE;

    @Column(length = 300)
    private String notes;

    public static ChequeBook create(CompanyBankAccount account, String prefix, long firstNumber, int leafCount,
                                    int numberWidth, LocalDate receivedDate, String notes) {
        ChequeBook book = new ChequeBook();
        book.assignScope(account.getCompanyId(), account.getBranchId());
        book.account = account;
        book.prefix = normalizePrefix(prefix);
        book.firstNumber = firstNumber;
        book.lastNumber = firstNumber + leafCount - 1;
        book.numberWidth = numberWidth;
        book.receivedDate = receivedDate;
        book.notes = Text.clean(notes);
        return book;
    }

    public static String normalizePrefix(String prefix) {
        String p = Text.code(prefix);
        return p == null ? "" : p;
    }

    public int getLeafCount() {
        return (int) (lastNumber - firstNumber + 1);
    }

    /** "CA 0001001" */
    public String format(long number) {
        return format(prefix, number, numberWidth);
    }

    public static String format(String prefix, long number, int width) {
        String digits = String.format("%0" + width + "d", number);
        return prefix == null || prefix.isEmpty() ? digits : prefix + " " + digits;
    }

    /** "CA 0001001 – CA 0001050" */
    public String getRangeLabel() {
        return format(firstNumber) + " – " + format(lastNumber);
    }

    /** Book is cancelled or lost: its remaining unused leaves follow. */
    public void close(BookStatus newStatus) {
        if (newStatus != BookStatus.CANCELLED && newStatus != BookStatus.LOST) {
            throw new IllegalArgumentException("Use CANCELLED or LOST");
        }
        if (status == BookStatus.CANCELLED || status == BookStatus.LOST) {
            throw new BusinessException("This book is already " + status.name().toLowerCase() + ".");
        }
        this.status = newStatus;
    }

    /** Called after leaf changes: no unused leaf left means the book is finished. */
    public void markFinished() {
        if (status == BookStatus.ACTIVE) {
            this.status = BookStatus.FINISHED;
        }
    }
}
