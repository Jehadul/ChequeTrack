package com.chequetrack.received.domain;

public enum ChequeType {
    CURRENT("Current", "Dated today or earlier; can be deposited now"),
    POST_DATED("Post-dated", "Dated in the future; deposit on or after its date"),
    SECURITY("Security", "Held as security; deposited only if the customer defaults");

    private final String label;
    private final String description;

    ChequeType(String label, String description) {
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
