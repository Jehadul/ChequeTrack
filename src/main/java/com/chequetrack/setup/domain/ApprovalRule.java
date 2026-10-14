package com.chequetrack.setup.domain;

import com.chequetrack.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

/**
 * One approval step for an action. A step is required when the amount is at
 * least {@code minAmount}; required steps are approved in {@code stepOrder}.
 * Example for ISSUE: step 1 Accounts Manager from 0, step 2 Administrator from
 * 5,00,000 means cheques of 5 lakh or more need both approvals.
 */
@Getter
@Entity
@Table(name = "approval_rule")
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalRule extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ApprovalAction action;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(name = "min_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "approver_role_id", nullable = false)
    private Long approverRoleId;

    @Column(nullable = false)
    private boolean active = true;

    public static ApprovalRule create(Long companyId, ApprovalAction action) {
        ApprovalRule rule = new ApprovalRule();
        rule.companyId = companyId;
        rule.action = action;
        return rule;
    }

    public void update(int stepOrder, BigDecimal minAmount, Long approverRoleId) {
        this.stepOrder = stepOrder;
        this.minAmount = minAmount;
        this.approverRoleId = approverRoleId;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean appliesTo(BigDecimal amount) {
        return active && amount.compareTo(minAmount) >= 0;
    }
}
