package com.chequetrack.issued.service;

import com.chequetrack.chequebook.service.ChequeBookService;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.common.service.DocumentNumberService;
import com.chequetrack.issued.IssuedPermissions;
import com.chequetrack.issued.domain.IssuedCheque;
import com.chequetrack.issued.domain.Payment;
import com.chequetrack.issued.repository.IssuedChequeRepository;
import com.chequetrack.issued.repository.PaymentRepository;
import com.chequetrack.lifecycle.domain.ChequeLifecycles;
import com.chequetrack.lifecycle.domain.StateMachine;
import com.chequetrack.lifecycle.domain.Transition;
import com.chequetrack.lifecycle.service.LifecycleRecord;
import com.chequetrack.lifecycle.service.LifecycleSupport;
import com.chequetrack.lifecycle.service.TransitionInput;
import com.chequetrack.organization.scope.BranchScope;
import com.chequetrack.setup.domain.CompanySettings;
import com.chequetrack.setup.service.SettingsService;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Connects issued cheques to the lifecycle engine: loading with access checks and the
 * side effects of each change (leaf status, supplier payment, bill settlement, defer
 * rules). Runs inside the engine's transaction.
 */
@Component
public class IssuedChequeLifecycle implements LifecycleSupport {

    public static final String PAYLOAD_DATE = "date";
    public static final String PAYLOAD_COLLECTED_BY = "collectedBy";
    public static final String PAYLOAD_NOTE = "handoverNote";

    private final IssuedChequeRepository chequeRepository;
    private final PaymentRepository paymentRepository;
    private final ChequeBookService chequeBookService;
    private final DocumentNumberService numbers;
    private final SettingsService settingsService;
    private final BranchScope branchScope;
    private final Clock clock;

    public IssuedChequeLifecycle(IssuedChequeRepository chequeRepository,
                                 PaymentRepository paymentRepository,
                                 ChequeBookService chequeBookService,
                                 DocumentNumberService numbers,
                                 SettingsService settingsService,
                                 BranchScope branchScope,
                                 Clock clock) {
        this.chequeRepository = chequeRepository;
        this.paymentRepository = paymentRepository;
        this.chequeBookService = chequeBookService;
        this.numbers = numbers;
        this.settingsService = settingsService;
        this.branchScope = branchScope;
        this.clock = clock;
    }

    @Override
    public String recordType() {
        return IssuedCheque.RECORD_TYPE;
    }

    @Override
    public StateMachine<?> machine() {
        return ChequeLifecycles.ISSUED;
    }

    @Override
    public String changePermission() {
        return IssuedPermissions.ISSUED_MANAGE;
    }

    @Override
    public String permissionFor(String action) {
        return "STOP_PAYMENT".equals(action) ? IssuedPermissions.ISSUED_STOP_PAYMENT : IssuedPermissions.ISSUED_MANAGE;
    }

    @Override
    public LifecycleRecord load(Long id) {
        IssuedCheque c = branchScope.require(chequeRepository.findById(id), "Issued cheque", id);
        c.getParty().getName();
        return c;
    }

    @Override
    public String url(Long id) {
        return "/issued/" + id;
    }

    @Override
    public void beforeChange(LifecycleRecord record, Transition transition, TransitionInput input) {
        IssuedCheque cheque = (IssuedCheque) record;
        LocalDate today = LocalDate.now(clock);
        CompanySettings settings = settingsService.forCompany(cheque.getCompanyId());
        switch (transition.action()) {
            case "SUBMIT" -> {
                if (cheque.expiryDate(settings.getChequeValidityDays()).isBefore(today)) {
                    throw new BusinessException("The cheque date " + cheque.getChequeDate() + " is stale. Change the date first.");
                }
            }
            case "PRINT" -> {
                cheque.printed(clock.instant());
                chequeBookService.finishIfEmpty(cheque.getLeaf().getBook());
            }
            case "REPRINT" -> throw new BusinessException("Reprinting arrives with cheque printing (M10).");
            case "HAND_OVER" -> {
                Object who = input.value(PAYLOAD_COLLECTED_BY);
                if (who == null || who.toString().isBlank()) {
                    throw new BusinessException("Enter who collected the cheque.");
                }
                if (who.toString().trim().length() > 150) {
                    throw new BusinessException("The name must be at most 150 characters.");
                }
                Object note = input.value(PAYLOAD_NOTE);
                if (note != null && note.toString().length() > 200) {
                    throw new BusinessException("The note must be at most 200 characters.");
                }
                LocalDate on = dateOr(input, today);
                if (on.isAfter(today)) {
                    throw new BusinessException("The hand-over date cannot be in the future.");
                }
                Payment payment = Payment.create(cheque, numbers.next(cheque.getCompanyId(), "PAY"));
                paymentRepository.save(payment);
                cheque.handedOver(who.toString(), on, note == null ? null : note.toString(), payment);
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
                            + cheque.expiryDate(settings.getChequeValidityDays()) + ". Void it and write a new cheque instead.");
                }
                cheque.deferred(newDate);
            }
            case "CLEAR" -> {
                LocalDate date = dateOr(input, today);
                if (date.isAfter(today)) {
                    throw new BusinessException("The clearing date cannot be in the future.");
                }
                if (date.isBefore(cheque.getChequeDate())) {
                    throw new BusinessException("The bank cannot pay before the cheque date " + cheque.getChequeDate() + ".");
                }
                cheque.cleared(date);
            }
            case "VOID" -> cheque.voided(input.reason());
            case "STOP_PAYMENT" -> cheque.stopped(input.reason());
            case "CANCEL" -> cheque.cancelled();
            default -> {
                // no side effects
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
}
