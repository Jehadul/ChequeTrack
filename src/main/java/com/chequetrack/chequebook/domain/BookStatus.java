package com.chequetrack.chequebook.domain;

public enum BookStatus {
    ACTIVE,
    FINISHED,
    CANCELLED,
    LOST;

    public boolean isClosed() {
        return this != ACTIVE;
    }
}
