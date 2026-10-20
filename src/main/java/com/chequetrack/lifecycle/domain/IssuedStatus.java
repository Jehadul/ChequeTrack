package com.chequetrack.lifecycle.domain;

/** Stages of a cheque the company writes to a supplier. Rules in {@link ChequeLifecycles#ISSUED}. */
public enum IssuedStatus implements LifecycleStatus {
    DRAFT("Draft", "Being prepared; can still be edited", false),
    PENDING_APPROVAL("Pending approval", "Waiting for the approvals its amount needs", false),
    APPROVED("Approved", "Approved and ready to print", false),
    PRINTED("Printed", "Printed on the leaf, not yet handed over", false),
    ISSUED("Issued", "Handed over to the payee", false),
    CLEARED("Cleared", "Paid by our bank", true),
    VOID("Void", "Cancelled after printing; the leaf is void and the payment reversed", true),
    STOPPED("Stopped", "Payment stopped at the bank", true),
    CANCELLED("Cancelled", "Cancelled before printing", true);

    private final String label;
    private final String description;
    private final boolean finalStatus;

    IssuedStatus(String label, String description, boolean finalStatus) {
        this.label = label;
        this.description = description;
        this.finalStatus = finalStatus;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean isFinalStatus() {
        return finalStatus;
    }
}
