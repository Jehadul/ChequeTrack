package com.chequetrack.lifecycle.service;

import com.chequetrack.lifecycle.domain.ApprovalRequest;

/**
 * Result of asking for a status change: either done, or waiting for approval.
 *
 * @param done            the change was carried out
 * @param approvalRequest the request created when approval is needed, otherwise null
 * @param message         text to show the user
 */
public record TransitionOutcome(boolean done, ApprovalRequest approvalRequest, String message) {

    static TransitionOutcome done(String message) {
        return new TransitionOutcome(true, null, message);
    }

    static TransitionOutcome pending(ApprovalRequest request, String message) {
        return new TransitionOutcome(false, request, message);
    }
}
