package com.chequetrack.setup.domain;

import com.chequetrack.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Cheque rules of one company. Exactly one row per company (seeded in V5). */
@Getter
@Entity
@Table(name = "company_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanySettings extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false, unique = true)
    private Long companyId;

    /** A cheque can be presented for this many days after its date (Bangladesh: 6 months). */
    @Column(name = "cheque_validity_days", nullable = false)
    private int chequeValidityDays;

    /** Warn this many days before a cheque becomes stale. */
    @Column(name = "near_expiry_days", nullable = false)
    private int nearExpiryDays;

    /** Remind this many days before a deposit or payment date. */
    @Column(name = "reminder_lead_days", nullable = false)
    private int reminderLeadDays;

    /** How often one cheque may be deferred. */
    @Column(name = "max_defers", nullable = false)
    private int maxDefers;

    /** Alert when a cheque book has fewer unused leaves than this. */
    @Column(name = "low_stock_leaves", nullable = false)
    private int lowStockLeaves;

    /** Days after a bounce within which the legal notice must be sent (NI Act 1881, s.138). */
    @Column(name = "bounce_notice_days", nullable = false)
    private int bounceNoticeDays;

    public void update(int chequeValidityDays, int nearExpiryDays, int reminderLeadDays, int maxDefers,
                       int lowStockLeaves, int bounceNoticeDays) {
        this.chequeValidityDays = chequeValidityDays;
        this.nearExpiryDays = nearExpiryDays;
        this.reminderLeadDays = reminderLeadDays;
        this.maxDefers = maxDefers;
        this.lowStockLeaves = lowStockLeaves;
        this.bounceNoticeDays = bounceNoticeDays;
    }
}
