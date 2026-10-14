package com.chequetrack.setup.domain;

/** Type of a company bank account. */
public enum AccountType {
    CURRENT("Current"),
    SAVINGS("Savings"),
    SND("Short notice deposit (SND)"),
    OVERDRAFT("Overdraft"),
    CASH_CREDIT("Cash credit");

    private final String label;

    AccountType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
