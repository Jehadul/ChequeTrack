package com.chequetrack.chequebook.domain;

public enum BookStatus {
    /** Has unused leaves that can be issued. */
    ACTIVE,
    /** Every leaf has been used, voided or otherwise closed. */
    FINISHED,
    /** Taken out of use (e.g. account closed); remaining leaves cancelled. */
    CANCELLED,
    /** Book lost or stolen; remaining leaves marked lost (stop-payment risk). */
    LOST;

    public boolean isClosed() {
        return this != ACTIVE;
    }
}
