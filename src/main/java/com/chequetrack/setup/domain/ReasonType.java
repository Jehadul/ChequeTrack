package com.chequetrack.setup.domain;

/** Lists of reasons users pick from when a cheque changes status. */
public enum ReasonType {
    BOUNCE("Bounce"),
    CANCEL("Cancel"),
    VOID("Void"),
    DEFER("Defer"),
    STOP_PAYMENT("Stop payment"),
    RETURN("Return"),
    REPLACE("Replace");

    private final String label;

    ReasonType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
