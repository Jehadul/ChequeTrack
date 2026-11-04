package com.chequetrack.printing.service;

import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.issued.domain.IssuedCheque;
import com.chequetrack.issued.service.IssuedChequePrintGuard;
import com.chequetrack.printing.domain.PrintLog;
import com.chequetrack.printing.repository.PrintLogRepository;
import com.chequetrack.security.service.CurrentUser;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

@Component
public class PrintLogGuard implements IssuedChequePrintGuard {

    private final PrintLogRepository logRepository;
    private final Clock clock;

    public PrintLogGuard(PrintLogRepository logRepository, Clock clock) {
        this.logRepository = logRepository;
        this.clock = clock;
    }

    @Override
    public void beforePrint(IssuedCheque cheque) {
        List<PrintLog> pending = logRepository.findPendingForCheque(cheque.getId());
        if (pending.isEmpty() || !cheque.getChequeNumber().equals(pending.get(0).getChequeNumber())) {
            throw new BusinessException("Print cheque " + cheque.getChequeNumber()
                    + " from its print page first, then confirm that it printed correctly.");
        }
        pending.get(0).decide(PrintLog.Outcome.OK, null, CurrentUser.get().getUsername(), clock.instant());
    }
}
