package com.chequetrack.received.service;

import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.common.service.DocumentNumberService;
import com.chequetrack.lifecycle.domain.ChequeLifecycles;
import com.chequetrack.lifecycle.domain.StateMachine;
import com.chequetrack.lifecycle.domain.Transition;
import com.chequetrack.lifecycle.service.LifecycleRecord;
import com.chequetrack.lifecycle.service.LifecycleSupport;
import com.chequetrack.lifecycle.service.TransitionInput;
import com.chequetrack.organization.scope.BranchScope;
import com.chequetrack.received.ReceivedPermissions;
import com.chequetrack.received.domain.DepositBatch;
import com.chequetrack.received.domain.Receipt;
import com.chequetrack.received.domain.ReceivedCheque;
import com.chequetrack.received.repository.DepositBatchRepository;
import com.chequetrack.received.repository.ReceiptRepository;
import com.chequetrack.received.repository.ReceivedChequeRepository;
import com.chequetrack.setup.domain.CompanySettings;
import com.chequetrack.setup.service.SettingsService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Connects received cheques to the lifecycle engine: loading with access checks and
 * the side effects of each change (deposit link, receipt, invoice settlement, bounce
 * figures, defer rules). Runs inside the engine's transaction.
 */
@Component
public class ReceivedChequeLifecycle implements LifecycleSupport {

    public static final String PAYLOAD_BATCH = "depositBatchId";
    public static final String PAYLOAD_DATE = "date";
    public static final String PAYLOAD_CHARGE = "bankCharge";

    private final ReceivedChequeRepository chequeRepository;
    private final DepositBatchRepository batchRepository;
    private final ReceiptRepository receiptRepository;
    private final DocumentNumberService numbers;
    private final SettingsService settingsService;
    private final BranchScope branchScope;
    private final Clock clock;

    public ReceivedChequeLifecycle(ReceivedChequeRepository chequeRepository,
                                   DepositBatchRepository batchRepository,
                                   ReceiptRepository receiptRepository,
                                   DocumentNumberService numbers,
                                   SettingsService settingsService,
                                   BranchScope branchScope,
                                   Clock clock) {
        this.chequeRepository = chequeRepository;
        this.batchRepository = batchRepository;
        this.receiptRepository = receiptRepository;
        this.numbers = numbers;
        this.settingsService = settingsService;
        this.branchScope = branchScope;
        this.clock = clock;
    }

    @Override
    public String recordType() {
        return ReceivedCheque.RECORD_TYPE;
    }

    @Override
    public StateMachine<?> machine() {
        return ChequeLifecycles.RECEIVED;
    }

    @Override
    public String changePermission() {
        return ReceivedPermissions.RECEIVED_MANAGE;
    }

    @Override
    public LifecycleRecord load(Long id) {
        ReceivedCheque c = branchScope.require(chequeRepository.findById(id), "Received cheque", id);
        c.getParty().getName();
        return c;
    }

    @Override
    public String url(Long id) {
        return "/received/" + id;
    }

    @Override
    public void beforeChange(LifecycleRecord record, Transition transition, TransitionInput input) {
        ReceivedCheque cheque = (ReceivedCheque) record;
        LocalDate today = LocalDate.now(clock);
        CompanySettings settings = settingsService.forCompany(cheque.getCompanyId());
        switch (transition.action()) {
            case "DEPOSIT" -> {
                Long batchId = asLong(input.value(PAYLOAD_BATCH));
                DepositBatch batch = batchId == null ? null : batchRepository.findById(batchId).orElse(null);
                if (batch == null) {
                    throw new BusinessException("Cheques are deposited through a deposit batch.");
                }
                if (cheque.getChequeDate().isAfter(batch.getDepositDate())) {
                    throw new BusinessException("Cheque " + cheque.getChequeNumber() + " is dated "
                            + cheque.getChequeDate() + " and cannot be deposited before that date.");
                }
                if (cheque.expiryDate(settings.getChequeValidityDays()).isBefore(batch.getDepositDate())) {
                    throw new BusinessException("Cheque " + cheque.getChequeNumber() + " is stale (older than "
                            + settings.getChequeValidityDays() + " days) and cannot be deposited.");
                }
                cheque.deposited(batch);
            }
            case "RECALL" -> cheque.recalled();
            case "CLEAR" -> {
                LocalDate date = dateOr(input, today);
                if (date.isAfter(today)) {
                    throw new BusinessException("The clearing date cannot be in the future.");
                }
                Receipt receipt = Receipt.create(cheque, numbers.next(cheque.getCompanyId(), "RCPT"), date);
                receiptRepository.save(receipt);
                cheque.cleared(receipt, date);
            }
            case "BOUNCE" -> {
                LocalDate date = dateOr(input, today);
                if (date.isAfter(today)) {
                    throw new BusinessException("The bounce date cannot be in the future.");
                }
                BigDecimal charge = asAmount(input.value(PAYLOAD_CHARGE));
                if (charge.signum() < 0) {
                    throw new BusinessException("The bank charge cannot be negative.");
                }
                cheque.bounced(input.reason(), date, charge);
            }
            case "REPRESENT" -> {
                if (cheque.expiryDate(settings.getChequeValidityDays()).isBefore(today)) {
                    throw new BusinessException("This cheque is stale and cannot be presented again. Ask the customer for a replacement.");
                }
                cheque.represented();
            }
            case "DEFER" -> {
                LocalDate newDate = dateOr(input, null);
                if (newDate == null) {
                    throw new BusinessException("Enter the new date.");
                }
                if (!newDate.isAfter(cheque.getChequeDate())) {
                    throw new BusinessException("The new date must be after the current date " + cheque.getChequeDate() + ".");
                }
                if (cheque.getDeferCount() >= settings.getMaxDefers()) {
                    throw new BusinessException("This cheque was already deferred " + cheque.getDeferCount()
                            + " time(s); the limit is " + settings.getMaxDefers() + ".");
                }
                if (newDate.isAfter(cheque.expiryDate(settings.getChequeValidityDays()))) {
                    throw new BusinessException("The new date is after the cheque expires on "
                            + cheque.expiryDate(settings.getChequeValidityDays()) + ". Ask the customer for a new cheque instead.");
                }
                cheque.deferred(newDate);
            }
            default -> {
                // RETURN, REPLACE, WRITE_OFF, CANCEL: only the status and reason change
            }
        }
    }

    private static LocalDate dateOr(TransitionInput input, LocalDate fallback) {
        Object v = input.value(PAYLOAD_DATE);
        if (v == null || v.toString().isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(v.toString());
        } catch (RuntimeException ex) {
            throw new BusinessException("Enter a valid date.");
        }
    }

    private static Long asLong(Object v) {
        if (v == null) {
            return null;
        }
        return v instanceof Number n ? n.longValue() : Long.valueOf(v.toString());
    }

    private static BigDecimal asAmount(Object v) {
        if (v == null || v.toString().isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(v.toString()).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new BusinessException("Enter a valid bank charge.");
        }
    }
}
