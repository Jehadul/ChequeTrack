package com.chequetrack.issued.domain;

public enum Crossing {
    ACCOUNT_PAYEE("A/C Payee only", "Paid only into the payee's bank account"),
    BEARER("Bearer", "Can be cashed over the counter by whoever presents it");

    private final String label;
    private final String description;

    Crossing(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
