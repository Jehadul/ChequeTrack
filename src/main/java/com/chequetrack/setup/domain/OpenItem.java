package com.chequetrack.setup.domain;

import com.chequetrack.common.domain.Text;
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

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A customer invoice or supplier bill that cheques settle. Settlement is
 * recorded by the cheque modules (M8 clearance, M9 issue); bounces and voids
 * re-open it. Branch-scoped.
 */
@Getter
@Entity
@Table(name = "open_item")
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OpenItem extends BranchScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "party_id", nullable = false)
    private Party party;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 10, updatable = false)
    private OpenItemType itemType;

    @Column(nullable = false, length = 30)
    private String number;

    @Column(name = "item_date", nullable = false)
    private LocalDate itemDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "settled_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal settledAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OpenItemStatus status = OpenItemStatus.OPEN;

    @Column(length = 300)
    private String narration;

    public static OpenItem create(Long companyId, Long branchId, OpenItemType type) {
        OpenItem item = new OpenItem();
        item.assignScope(companyId, branchId);
        item.itemType = type;
        return item;
    }

    public void update(Party party, String number, LocalDate itemDate, LocalDate dueDate, BigDecimal amount,
                       String narration) {
        if (status != OpenItemStatus.OPEN) {
            throw new BusinessException("Only open items can be edited.");
        }
        this.party = party;
        this.number = number.trim().toUpperCase();
        this.itemDate = itemDate;
        this.dueDate = dueDate;
        this.amount = amount;
        this.narration = Text.clean(narration);
    }

    public void cancel() {
        if (status == OpenItemStatus.CANCELLED) {
            throw new BusinessException("Already cancelled.");
        }
        if (settledAmount.signum() != 0) {
            throw new BusinessException("This " + itemType.getLabel().toLowerCase()
                    + " is already partly settled by a cheque and cannot be cancelled.");
        }
        this.status = OpenItemStatus.CANCELLED;
    }

    /**
     * A cheque paid part or all of this item. Never settles more than is outstanding.
     * @return the amount actually settled (kept by the cheque so a bounce can reverse exactly that)
     */
    public BigDecimal applySettlement(BigDecimal wanted) {
        if (status == OpenItemStatus.CANCELLED || wanted.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal applied = wanted.min(getOutstanding());
        settledAmount = settledAmount.add(applied);
        refreshStatus();
        return applied;
    }

    /** The cheque that settled this item bounced: the item is open again by that amount. */
    public void reverseSettlement(BigDecimal amount) {
        settledAmount = settledAmount.subtract(amount).max(BigDecimal.ZERO);
        refreshStatus();
    }

    private void refreshStatus() {
        if (status == OpenItemStatus.CANCELLED) {
            return;
        }
        if (settledAmount.signum() == 0) {
            status = OpenItemStatus.OPEN;
        } else if (settledAmount.compareTo(amount) >= 0) {
            status = OpenItemStatus.SETTLED;
        } else {
            status = OpenItemStatus.PARTLY_SETTLED;
        }
    }

    public BigDecimal getOutstanding() {
        return status == OpenItemStatus.CANCELLED ? BigDecimal.ZERO : amount.subtract(settledAmount);
    }
}
