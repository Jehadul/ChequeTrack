package com.chequetrack.lifecycle.domain;

public enum StepStatus {
    WAITING, APPROVED, REJECTED,
    /** The request was rejected or withdrawn before this step was reached. */
    NOT_NEEDED
}
