package com.chequetrack.printing.domain;

public enum FieldCode {
    DATE("Date"),
    PAYEE("Payee name"),
    AMOUNT_WORDS("Amount in words"),
    AMOUNT_FIGURES("Amount in figures"),
    CROSSING("Crossing (A/C Payee)"),
    SIGNATORY("Signatory");

    private final String label;

    FieldCode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
