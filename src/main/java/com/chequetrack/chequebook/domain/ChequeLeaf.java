package com.chequetrack.chequebook.domain;

import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.organization.scope.BranchScopedEntity;
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

@Getter
@Entity
@Table(name = "cheque_leaf")
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChequeLeaf extends BranchScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false, updatable = false)
    private ChequeBook book;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(nullable = false, length = 10, updatable = false)
    private String prefix;

    @Column(name = "leaf_number", nullable = false, updatable = false)
    private long leafNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeafStatus status;

    public void voidUnused() {
        if (status != LeafStatus.UNUSED) {
            throw new BusinessException(
                    "Only unused leaves can be voided here. Issued cheques are voided from the cheque itself.");
        }
        this.status = LeafStatus.VOID;
    }

    public void allocate() {
        if (status != LeafStatus.UNUSED) {
            throw new BusinessException("Leaf " + getDisplayNumber() + " is no longer free.");
        }
        this.status = LeafStatus.ALLOCATED;
    }

    public void release() {
        if (status == LeafStatus.ALLOCATED) {
            this.status = LeafStatus.UNUSED;
        }
    }

    public void markUsed() {
        if (status != LeafStatus.ALLOCATED && status != LeafStatus.USED) {
            throw new BusinessException("Leaf " + getDisplayNumber() + " is " + status.name().toLowerCase() + ".");
        }
        this.status = LeafStatus.USED;
    }

    public void voidWithCheque() {
        this.status = LeafStatus.VOID;
    }

    public void closeWithBook(BookStatus bookStatus) {
        if (status == LeafStatus.UNUSED) {
            this.status = bookStatus == BookStatus.LOST ? LeafStatus.LOST : LeafStatus.CANCELLED;
        }
    }

    public String getDisplayNumber() {
        return book.format(leafNumber);
    }
}
