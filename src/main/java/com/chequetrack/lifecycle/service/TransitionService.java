package com.chequetrack.lifecycle.service;

import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.event.StatusChangedEvent;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.common.exception.NotFoundException;
import com.chequetrack.lifecycle.domain.ApprovalRequest;
import com.chequetrack.lifecycle.domain.ApprovalStatus;
import com.chequetrack.lifecycle.domain.StateMachine;
import com.chequetrack.lifecycle.domain.Transition;
import com.chequetrack.lifecycle.repository.ApprovalRequestRepository;
import com.chequetrack.security.service.AppUserPrincipal;
import com.chequetrack.security.service.CurrentUser;
import com.chequetrack.setup.service.ApprovalRuleService;
import com.chequetrack.setup.service.ApprovalRuleService.RequiredStep;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The single gate for every cheque status change (ARCHITECTURE.md section 6).
 * In one transaction it:
 * <ol>
 *   <li>checks the change is allowed from the current status;</li>
 *   <li>checks a reason is given when required, and that no approval is already pending;</li>
 *   <li>checks the approval rules: if approval is needed, creates an approval request and stops;</li>
 *   <li>otherwise runs the module's side effects, sets the new status, writes status history and the audit log.</li>
 * </ol>
 * If any step fails, nothing is saved.
 */
@Service
public class TransitionService {

    private final Map<String, LifecycleSupport> supports;
    private final ApprovalRequestRepository requestRepository;
    private final ApprovalRuleService approvalRuleService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public TransitionService(List<LifecycleSupport> supports,
                             ApprovalRequestRepository requestRepository,
                             ApprovalRuleService approvalRuleService,
                             ApplicationEventPublisher events,
                             Clock clock) {
        this.supports = supports.stream().collect(Collectors.toMap(LifecycleSupport::recordType, Function.identity()));
        this.requestRepository = requestRepository;
        this.approvalRuleService = approvalRuleService;
        this.events = events;
        this.clock = clock;
    }

    /** Asks for a status change of a record: carried out now, or sent for approval. */
    @Transactional
    public TransitionOutcome request(String recordType, Long recordId, String action, TransitionInput input) {
        LifecycleSupport support = support(recordType);
        String permission = support.permissionFor(action);
        if (!CurrentUser.get().hasPermission(permission)) {
            throw new AccessDeniedException("Missing permission " + permission);
        }
        LifecycleRecord record = support.load(recordId);
        Transition transition = allowedTransition(support.machine(), record, action);
        String reason = input.reason() == null ? null : input.reason().trim();
        if (transition.reasonRequired() && (reason == null || reason.isEmpty())) {
            throw new BusinessException("Enter the reason for '" + transition.label().toLowerCase() + "'.");
        }
        if (reason != null && reason.length() > 500) {
            throw new BusinessException("The reason must be at most 500 characters.");
        }
        if (requestRepository.findByRecordTypeAndRecordIdAndStatus(recordType, recordId, ApprovalStatus.PENDING).isPresent()) {
            throw new BusinessException("This cheque is waiting for an approval. Wait for the decision or withdraw the request first.");
        }

        List<RequiredStep> steps = transition.approval() == null ? List.of()
                : approvalRuleService.requiredSteps(record.getCompanyId(), transition.approval(), record.approvalAmount());
        if (steps.isEmpty()) {
            execute(support, record, transition, TransitionInput.of(reason, input.payload()));
            return TransitionOutcome.done(transition.label() + " done.");
        }

        AppUserPrincipal user = CurrentUser.get();
        ApprovalRequest request = ApprovalRequest.create(record.getCompanyId(), record.getBranchId(), recordType, recordId,
                record.lifecycleLabel(), transition.approval(), transition.action(), record.approvalAmount(), reason,
                input.payload(), user.getId(), user.getUsername(), clock.instant());
        steps.forEach(s -> request.addStep(s.stepOrder(), s.roleId(), s.roleName()));
        requestRepository.save(request);

        if (transition.pendingStatus() != null) {
            changeStatus(record, recordType, transition.pendingStatus(),
                    "Sent for approval (" + steps.size() + " step" + (steps.size() > 1 ? "s" : "") + ")");
        }
        events.publishEvent(AuditEvent.of("APPROVAL_REQUESTED", recordType, recordId,
                transition.label() + " sent for approval: " + steps.stream().map(RequiredStep::roleName)
                        .collect(Collectors.joining(" → ")) + (reason == null ? "" : ". Reason: " + reason)));
        String who = steps.get(0).roleName();
        return TransitionOutcome.pending(request, "Sent for approval. Waiting for " + who + ".");
    }

    /** Actions a user could start now on this record, for showing buttons. */
    public List<Transition> availableActions(String recordType, String currentStatus) {
        return support(recordType).machine().availableFrom(currentStatus);
    }

    /** Carries out an approved request (called by ApprovalService, inside its transaction). */
    void executeApproved(ApprovalRequest request) {
        LifecycleSupport support = support(request.getRecordType());
        LifecycleRecord record = support.load(request.getRecordId());
        Transition transition = support.machine().find(request.getTransitionAction())
                .orElseThrow(() -> new IllegalStateException("Unknown action " + request.getTransitionAction()));
        // While waiting the record must still be in the pending status (e.g. PENDING_APPROVAL),
        // or, for actions without one, still in a status the action is allowed from.
        boolean stillValid = transition.pendingStatus() != null
                ? transition.pendingStatus().equals(record.lifecycleStatus())
                : transition.allowedFrom(record.lifecycleStatus());
        if (!stillValid) {
            throw new BusinessException("The cheque changed since the request was made ("
                    + support.machine().label(record.lifecycleStatus()) + "). The request cannot be carried out; reject it instead.");
        }
        execute(support, record, transition, TransitionInput.of(request.getReason(), request.getPayload()));
    }

    /** A rejected or withdrawn request puts the record back where it was (e.g. PENDING_APPROVAL → DRAFT). */
    void undoPending(ApprovalRequest request, String why) {
        LifecycleSupport support = support(request.getRecordType());
        Transition transition = support.machine().find(request.getTransitionAction()).orElseThrow();
        if (transition.pendingStatus() != null && transition.rejectedStatus() != null) {
            LifecycleRecord record = support.load(request.getRecordId());
            if (record.lifecycleStatus().equals(transition.pendingStatus())) {
                changeStatus(record, request.getRecordType(), transition.rejectedStatus(), why);
            }
        }
    }

    String recordUrl(String recordType, Long recordId) {
        LifecycleSupport support = supports.get(recordType);
        return support == null ? null : support.url(recordId);
    }

    // ---------------------------------------------------------------------------------------------

    private void execute(LifecycleSupport support, LifecycleRecord record, Transition transition, TransitionInput input) {
        support.beforeChange(record, transition, input);
        String detail = input.reason() == null ? transition.label() : transition.label() + ": " + input.reason();
        changeStatus(record, support.recordType(), transition.to(), detail);
        events.publishEvent(AuditEvent.of("CHEQUE_" + transition.action(), support.recordType(), record.getId(),
                transition.label() + " – " + record.lifecycleLabel() + (input.reason() == null ? "" : ". Reason: " + input.reason())));
    }

    private void changeStatus(LifecycleRecord record, String recordType, String newStatus, String reason) {
        String from = record.lifecycleStatus();
        record.applyLifecycleStatus(newStatus);
        events.publishEvent(new StatusChangedEvent(recordType, record.getId(), record.getCompanyId(), record.getBranchId(),
                from, newStatus, reason));
    }

    private static Transition allowedTransition(StateMachine<?> machine, LifecycleRecord record, String action) {
        Transition transition = machine.find(action)
                .orElseThrow(() -> new BusinessException("Unknown action '" + action + "'."));
        if (!transition.allowedFrom(record.lifecycleStatus())) {
            throw new BusinessException("'" + transition.label() + "' is not possible when the cheque is "
                    + machine.label(record.lifecycleStatus()).toLowerCase() + ". Allowed when: "
                    + machine.fromLabels(transition).toLowerCase() + ".");
        }
        return transition;
    }

    private LifecycleSupport support(String recordType) {
        LifecycleSupport support = supports.get(recordType);
        if (support == null) {
            throw new NotFoundException("Record type", recordType);
        }
        return support;
    }
}
