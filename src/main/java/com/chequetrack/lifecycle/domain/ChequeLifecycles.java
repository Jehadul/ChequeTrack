package com.chequetrack.lifecycle.domain;

import com.chequetrack.setup.domain.ApprovalAction;

import java.util.EnumSet;

import static com.chequetrack.lifecycle.domain.IssuedStatus.APPROVED;
import static com.chequetrack.lifecycle.domain.IssuedStatus.DRAFT;
import static com.chequetrack.lifecycle.domain.IssuedStatus.PENDING_APPROVAL;
import static com.chequetrack.lifecycle.domain.IssuedStatus.PRINTED;
import static com.chequetrack.lifecycle.domain.IssuedStatus.STOPPED;
import static com.chequetrack.lifecycle.domain.ReceivedStatus.BOUNCED;
import static com.chequetrack.lifecycle.domain.ReceivedStatus.DEPOSITED;
import static com.chequetrack.lifecycle.domain.ReceivedStatus.IN_HAND;
import static com.chequetrack.lifecycle.domain.ReceivedStatus.REPLACED;
import static com.chequetrack.lifecycle.domain.ReceivedStatus.RETURNED;
import static com.chequetrack.lifecycle.domain.ReceivedStatus.WRITTEN_OFF;

/**
 * The single definition of which status changes are allowed (Objective 1).
 * The "Cheque lifecycle" page renders these tables, so the documentation
 * can never drift from the code.
 */
public final class ChequeLifecycles {

    /** Cheques received from customers. */
    public static final StateMachine<ReceivedStatus> RECEIVED = StateMachine
            .builder("Received cheque", ReceivedStatus.class, IN_HAND)
            .on("DEPOSIT", "Deposit", EnumSet.of(IN_HAND), DEPOSITED, false)
            .on("RECALL", "Take back from deposit", EnumSet.of(DEPOSITED), IN_HAND, true)
            .on("CLEAR", "Mark cleared", EnumSet.of(DEPOSITED), ReceivedStatus.CLEARED, false)
            .on("BOUNCE", "Record bounce", EnumSet.of(DEPOSITED, ReceivedStatus.CLEARED), BOUNCED, true)
            .on("REPRESENT", "Present again", EnumSet.of(BOUNCED), IN_HAND, false)
            .onApproved("DEFER", "Defer", EnumSet.of(IN_HAND, BOUNCED), IN_HAND, ApprovalAction.DEFER, true)
            .on("RETURN", "Return to customer", EnumSet.of(IN_HAND, BOUNCED), RETURNED, true)
            .on("REPLACE", "Replace with new cheque", EnumSet.of(IN_HAND, BOUNCED), REPLACED, true)
            .onApproved("WRITE_OFF", "Write off", EnumSet.of(BOUNCED), WRITTEN_OFF, ApprovalAction.WRITE_OFF, true)
            .on("CANCEL", "Cancel entry", EnumSet.of(IN_HAND), ReceivedStatus.CANCELLED, true)
            .build();

    /** Cheques the company writes. */
    public static final StateMachine<IssuedStatus> ISSUED = StateMachine
            .builder("Issued cheque", IssuedStatus.class, DRAFT)
            .onApprovedVia("SUBMIT", "Submit for approval", EnumSet.of(DRAFT), APPROVED, ApprovalAction.ISSUE,
                    PENDING_APPROVAL, DRAFT)
            .on("CANCEL", "Cancel", EnumSet.of(DRAFT, APPROVED), IssuedStatus.CANCELLED, true)
            .on("PRINT", "Print", EnumSet.of(APPROVED), PRINTED, false)
            .onApproved("REPRINT", "Reprint", EnumSet.of(PRINTED), PRINTED, ApprovalAction.REPRINT, true)
            .on("HAND_OVER", "Hand over", EnumSet.of(PRINTED), IssuedStatus.ISSUED, false)
            .onApproved("DEFER", "Defer", EnumSet.of(IssuedStatus.ISSUED), IssuedStatus.ISSUED, ApprovalAction.DEFER, true)
            .on("CLEAR", "Mark cleared", EnumSet.of(IssuedStatus.ISSUED), IssuedStatus.CLEARED, false)
            .onApproved("VOID", "Void", EnumSet.of(PRINTED, IssuedStatus.ISSUED), IssuedStatus.VOID, ApprovalAction.VOID, true)
            .onApproved("STOP_PAYMENT", "Stop payment", EnumSet.of(IssuedStatus.ISSUED), STOPPED, ApprovalAction.STOP_PAYMENT, true)
            .build();

    private ChequeLifecycles() {
    }
}
