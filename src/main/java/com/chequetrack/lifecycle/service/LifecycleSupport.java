package com.chequetrack.lifecycle.service;

import com.chequetrack.lifecycle.domain.StateMachine;
import com.chequetrack.lifecycle.domain.Transition;

public interface LifecycleSupport {

    String recordType();

    StateMachine<?> machine();

    String changePermission();

    default String permissionFor(String action) {
        return changePermission();
    }

    LifecycleRecord load(Long id);

    String url(Long id);

    void beforeChange(LifecycleRecord record, Transition transition, TransitionInput input);
}
