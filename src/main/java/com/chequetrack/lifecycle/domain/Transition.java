package com.chequetrack.lifecycle.domain;

import com.chequetrack.setup.domain.ApprovalAction;

import java.util.Set;

/**
 * One allowed status change: an action that moves a record from one of {@code from}
 * to {@code to}.
 *
 * @param action         code used by the code and the audit log, e.g. "DEPOSIT"
 * @param label          button text, e.g. "Deposit"
 * @param from           statuses the action is allowed in
 * @param to             status after the action (may equal the current one, e.g. DEFER)
 * @param approval       approval rule list that applies, or null when no approval is ever needed
 * @param pendingStatus  status while the approval is pending (e.g. PENDING_APPROVAL), or null to keep the current one
 * @param rejectedStatus status when the approval is rejected or withdrawn; only used with pendingStatus
 * @param reasonRequired the user must give a reason
 */
public record Transition(String action, String label, Set<String> from, String to, ApprovalAction approval,
                         String pendingStatus, String rejectedStatus, boolean reasonRequired) {

    public boolean allowedFrom(String status) {
        return from.contains(status);
    }
}
