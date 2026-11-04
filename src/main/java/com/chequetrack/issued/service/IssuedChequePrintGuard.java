package com.chequetrack.issued.service;

import com.chequetrack.issued.domain.IssuedCheque;

public interface IssuedChequePrintGuard {

    /** Throw a BusinessException to refuse. */
    void beforePrint(IssuedCheque cheque);
}
