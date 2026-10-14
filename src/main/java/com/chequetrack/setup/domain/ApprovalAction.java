package com.chequetrack.setup.domain;

/** Sensitive actions that need approval (rules in {@link ApprovalRule}, enforced in M7). */
public enum ApprovalAction {
    ISSUE("Issue a cheque"),
    VOID("Void a cheque"),
    REPRINT("Reprint a cheque"),
    DEFER("Defer a cheque"),
    WRITE_OFF("Write off a bounced cheque"),
    STOP_PAYMENT("Stop payment");

    private final String label;

    ApprovalAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
