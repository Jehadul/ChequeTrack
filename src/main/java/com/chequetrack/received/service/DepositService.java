package com.chequetrack.received.service;

import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.common.service.DocumentNumberService;
import com.chequetrack.lifecycle.service.TransitionInput;
import com.chequetrack.lifecycle.service.TransitionService;
import com.chequetrack.organization.scope.BranchScope;
import com.chequetrack.received.ReceivedPermissions;
import com.chequetrack.received.domain.DepositBatch;
import com.chequetrack.received.domain.ReceivedCheque;
import com.chequetrack.received.dto.DepositForm;
import com.chequetrack.received.repository.DepositBatchRepository;
import com.chequetrack.received.repository.ReceivedChequeRepository;
import com.chequetrack.setup.domain.CompanyBankAccount;
import com.chequetrack.setup.service.CompanyAccountService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Deposit batches: cheques given to the bank together with one slip. Every cheque is
 * moved to DEPOSITED through the lifecycle engine; if any cheque is refused (post-dated,
 * stale, wrong status) the whole batch is rolled back.
 */
@Service
public class DepositService {

    private static final String VIEW = "hasAuthority('" + ReceivedPermissions.RECEIVED_VIEW + "')";
    private static final String MANAGE = "hasAuthority('" + ReceivedPermissions.RECEIVED_MANAGE + "')";

    private final DepositBatchRepository batchRepository;
    private final ReceivedChequeRepository chequeRepository;
    private final CompanyAccountService accountService;
    private final TransitionService transitionService;
    private final DocumentNumberService numbers;
    private final BranchScope branchScope;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public DepositService(DepositBatchRepository batchRepository,
                          ReceivedChequeRepository chequeRepository,
                          CompanyAccountService accountService,
                          TransitionService transitionService,
                          DocumentNumberService numbers,
                          BranchScope branchScope,
                          ApplicationEventPublisher events,
                          Clock clock) {
        this.batchRepository = batchRepository;
        this.chequeRepository = chequeRepository;
        this.accountService = accountService;
        this.transitionService = transitionService;
        this.numbers = numbers;
        this.branchScope = branchScope;
        this.events = events;
        this.clock = clock;
    }

    /** Cheques in hand that can be deposited on the given date. */
    @Transactional(readOnly = true)
    @PreAuthorize(MANAGE)
    public List<ReceivedCheque> dueForDeposit(LocalDate date) {
        return chequeRepository.findDueForDeposit(date);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public List<DepositBatch> recent() {
        return batchRepository.findRecent(Limit.of(100));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public DepositBatch get(Long id) {
        return branchScope.require(batchRepository.findWithAccount(id), "Deposit batch", id);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public List<ReceivedCheque> chequesOf(Long batchId) {
        get(batchId);
        return chequeRepository.findByBatch(batchId);
    }

    @Transactional
    @PreAuthorize(MANAGE)
    public DepositBatch create(DepositForm form) {
        CompanyBankAccount account = accountService.get(form.getAccountId());
        if (!account.isActive()) {
            throw new BusinessException("Account " + account.getLabel() + " is inactive.");
        }
        if (form.getDepositDate().isAfter(LocalDate.now(clock))) {
            throw new BusinessException("The deposit date cannot be in the future.");
        }
        DepositBatch batch = DepositBatch.create(account, numbers.next(account.getCompanyId(), "DEP"),
                form.getDepositDate(), form.getNotes());
        batchRepository.saveAndFlush(batch);

        for (Long chequeId : new LinkedHashSet<>(form.getChequeIds())) {
            transitionService.request(ReceivedCheque.RECORD_TYPE, chequeId, "DEPOSIT",
                    TransitionInput.of("Deposit batch " + batch.getNumber(),
                            Map.of(ReceivedChequeLifecycle.PAYLOAD_BATCH, batch.getId())));
            batch.addCheque(chequeRepository.findById(chequeId).orElseThrow().getAmount());
        }
        events.publishEvent(AuditEvent.of("DEPOSIT_BATCH_CREATED", "DepositBatch", batch.getId(),
                "Deposit " + batch.getNumber() + ": " + batch.getChequeCount() + " cheque(s), Tk "
                        + batch.getTotalAmount().toPlainString() + " into " + account.getLabel()));
        return batch;
    }
}
