package com.chequetrack.lifecycle.service;

import java.math.BigDecimal;

/**
 * A record whose status is managed by the lifecycle engine (received and issued
 * cheques, from M8/M9). Statuses are passed by enum name.
 */
public interface LifecycleRecord {

    Long getId();

    Long getCompanyId();

    Long getBranchId();

    /** Current status, as the enum name. */
    String lifecycleStatus();

    /**
     * Sets the new status. Only {@link TransitionService} calls this, after
     * checking the change is allowed.
     */
    void applyLifecycleStatus(String newStatus);

    /** Amount the approval rules are checked against. */
    BigDecimal approvalAmount();

    /** Short description for approval lists and the audit log, e.g. "Cheque 0001005 · Karim Steel · Tk 50,000.00". */
    String lifecycleLabel();
}
