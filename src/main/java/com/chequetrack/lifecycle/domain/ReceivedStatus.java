package com.chequetrack.lifecycle.domain;

/** Stages of a cheque received from a customer. Rules in {@link ChequeLifecycles#RECEIVED}. */
public enum ReceivedStatus implements LifecycleStatus {
    IN_HAND("In hand", "Received and kept safe; waiting for its date or for deposit", false),
    DEPOSITED("Deposited", "Given to the bank, waiting for clearing", false),
    CLEARED("Cleared", "Paid by the drawer's bank; customer receipt created (the bank can still return it)", false),
    BOUNCED("Bounced", "Returned unpaid by the bank", false),
    RETURNED("Returned", "Given back to the customer", true),
    REPLACED("Replaced", "Exchanged for a new cheque from the customer", true),
    WRITTEN_OFF("Written off", "Bounced and no longer expected to be paid", true),
    CANCELLED("Cancelled", "Entered by mistake", true);

    private final String label;
    private final String description;
    private final boolean finalStatus;

    ReceivedStatus(String label, String description, boolean finalStatus) {
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
