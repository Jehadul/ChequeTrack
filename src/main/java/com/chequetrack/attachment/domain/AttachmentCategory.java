package com.chequetrack.attachment.domain;

public enum AttachmentCategory {
    CHEQUE_SCAN("Cheque scan"),
    INVOICE("Invoice"),
    PURCHASE_ORDER("Purchase order"),
    DEPOSIT_SLIP("Deposit slip"),
    OTHER("Other");

    private final String label;

    AttachmentCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
