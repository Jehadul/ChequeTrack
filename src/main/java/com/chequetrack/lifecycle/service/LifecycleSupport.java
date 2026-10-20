package com.chequetrack.lifecycle.service;

import com.chequetrack.lifecycle.domain.StateMachine;
import com.chequetrack.lifecycle.domain.Transition;

/**
 * Connects one record type to the lifecycle engine. Implemented as a Spring bean
 * by the module that owns the records (M8 received cheques, M9 issued cheques),
 * so this module never depends on them.
 */
public interface LifecycleSupport {

    /** Record type name used in approval requests, status history and the audit log, e.g. "ReceivedCheque". */
    String recordType();

    StateMachine<?> machine();

    /**
     * Permission a user needs to ask for a change of these records. Not checked when
     * an approved request is carried out: the approval itself is the authority then.
     */
    String changePermission();

    /**
     * Loads the record for a change, checking the current user may see it.
     * @throws com.chequetrack.common.exception.NotFoundException otherwise
     */
    LifecycleRecord load(Long id);

    /** Page of the record, for links from the approval inbox. */
    String url(Long id);

    /**
     * Module-specific checks and side effects of a change, run inside the same
     * transaction just before the new status is set (e.g. create the customer
     * receipt on CLEAR, reverse the payment on VOID). Throw a BusinessException to refuse.
     */
    void beforeChange(LifecycleRecord record, Transition transition, TransitionInput input);
}
