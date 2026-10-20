package com.chequetrack.lifecycle.domain;

/** Common view of the status enums, for pages that show any lifecycle. */
public interface LifecycleStatus {

    String name();

    String getLabel();

    String getDescription();

    /** No further changes are possible. */
    boolean isFinalStatus();
}
