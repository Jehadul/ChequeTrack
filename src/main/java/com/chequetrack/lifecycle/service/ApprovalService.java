package com.chequetrack.lifecycle.service;

import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.lifecycle.LifecyclePermissions;
import com.chequetrack.lifecycle.domain.ApprovalRequest;
import com.chequetrack.lifecycle.domain.ApprovalStep;
import com.chequetrack.lifecycle.repository.ApprovalRequestRepository;
import com.chequetrack.organization.repository.RoleRepository;
import com.chequetrack.organization.scope.BranchScope;
import com.chequetrack.security.service.AppUserPrincipal;
import com.chequetrack.security.service.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Approval inbox and decisions. Rules for deciding a step:
 * <ul>
 *   <li>the user has the step's role and the APPROVAL_DECIDE permission;</li>
 *   <li>the user did not make the request (no approving your own change);</li>
 *   <li>the user has not approved an earlier step of the same request (two people for two steps);</li>
 *   <li>the request's branch is one the user may see (branch filter + BranchScope).</li>
 * </ul>
 * The last approval carries out the change in the same transaction.
 */
@Service
public class ApprovalService {

    private static final String DECIDE = "hasAuthority('" + LifecyclePermissions.APPROVAL_DECIDE + "')";

    private final ApprovalRequestRepository requestRepository;
    private final TransitionService transitionService;
    private final RoleRepository roleRepository;
    private final BranchScope branchScope;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ApprovalService(ApprovalRequestRepository requestRepository,
                           TransitionService transitionService,
                           RoleRepository roleRepository,
                           BranchScope branchScope,
                           ApplicationEventPublisher events,
                           Clock clock) {
        this.requestRepository = requestRepository;
        this.transitionService = transitionService;
        this.roleRepository = roleRepository;
        this.branchScope = branchScope;
        this.events = events;
        this.clock = clock;
    }

    /** Requests waiting for the current user's role. */
    @Transactional(readOnly = true)
    @PreAuthorize(DECIDE)
    public List<ApprovalRequest> inbox() {
        AppUserPrincipal me = CurrentUser.get();
        List<ApprovalRequest> list = requestRepository.findInbox(myRoleId(me), me.getId());
        list.forEach(r -> r.getSteps().size());  // load steps for the page
        return list;
    }

    /** Number of requests waiting for the current user's role (sidebar badge). */
    @Transactional(readOnly = true)
    public long inboxCount() {
        AppUserPrincipal me = CurrentUser.get();
        if (!me.hasPermission(LifecyclePermissions.APPROVAL_DECIDE)) {
            return 0;
        }
        return requestRepository.countInbox(myRoleId(me), me.getId());
    }

    /** The current user's own requests, newest first. */
    @Transactional(readOnly = true)
    public List<ApprovalRequest> mine() {
        List<ApprovalRequest> list = requestRepository.findByRequestedByIdOrderByRequestedAtDesc(CurrentUser.get().getId(), Limit.of(100));
        list.forEach(r -> r.getSteps().size());
        return list;
    }

    /** Requests of one record (for cheque pages), newest first. */
    @Transactional(readOnly = true)
    public List<ApprovalRequest> forRecord(String recordType, Long recordId) {
        List<ApprovalRequest> list = requestRepository.findByRecordTypeAndRecordIdOrderByRequestedAtDesc(recordType, recordId);
        list.forEach(r -> r.getSteps().size());
        return list;
    }

    @Transactional(readOnly = true)
    public ApprovalRequest get(Long id) {
        ApprovalRequest request = branchScope.require(requestRepository.findById(id), "Approval request", id);
        request.getSteps().size();
        return request;
    }

    /** Whether the current user may decide the waiting step now (for showing the buttons). */
    @Transactional(readOnly = true)
    public boolean canDecide(ApprovalRequest request) {
        try {
            checkCanDecide(request, CurrentUser.get());
            return true;
        } catch (BusinessException ex) {
            return false;
        }
    }

    public String recordUrl(ApprovalRequest request) {
        return transitionService.recordUrl(request.getRecordType(), request.getRecordId());
    }

    @Transactional
    @PreAuthorize(DECIDE)
    public ApprovalRequest approve(Long id, String comment) {
        AppUserPrincipal me = CurrentUser.get();
        ApprovalRequest request = get(id);
        ApprovalStep step = checkCanDecide(request, me);
        checkComment(comment, false);
        boolean last = request.approveStep(me.getId(), me.getUsername(), comment, clock.instant());
        events.publishEvent(AuditEvent.of("APPROVAL_GRANTED", request.getRecordType(), request.getRecordId(),
                "Approved step " + step.getStepOrder() + " (" + step.getRoleName() + ") of '" + request.getTransitionAction()
                        + "' for " + request.getRecordLabel() + (comment == null || comment.isBlank() ? "" : ": " + comment.trim())));
        if (last) {
            transitionService.executeApproved(request);
        }
        return request;
    }

    @Transactional
    @PreAuthorize(DECIDE)
    public ApprovalRequest reject(Long id, String comment) {
        AppUserPrincipal me = CurrentUser.get();
        ApprovalRequest request = get(id);
        ApprovalStep step = checkCanDecide(request, me);
        checkComment(comment, true);
        request.reject(me.getId(), me.getUsername(), comment.trim(), clock.instant());
        transitionService.undoPending(request, "Rejected by " + me.getUsername() + ": " + comment.trim());
        events.publishEvent(AuditEvent.of("APPROVAL_REJECTED", request.getRecordType(), request.getRecordId(),
                "Rejected step " + step.getStepOrder() + " (" + step.getRoleName() + ") of '" + request.getTransitionAction()
                        + "' for " + request.getRecordLabel() + ": " + comment.trim()));
        return request;
    }

    /** The requester takes back their own pending request. */
    @Transactional
    public ApprovalRequest withdraw(Long id) {
        AppUserPrincipal me = CurrentUser.get();
        ApprovalRequest request = get(id);
        if (!request.getRequestedById().equals(me.getId())) {
            throw new BusinessException("Only the person who made the request can withdraw it.");
        }
        request.withdraw(clock.instant());
        transitionService.undoPending(request, "Request withdrawn by " + me.getUsername());
        events.publishEvent(AuditEvent.of("APPROVAL_WITHDRAWN", request.getRecordType(), request.getRecordId(),
                "Withdrew the request '" + request.getTransitionAction() + "' for " + request.getRecordLabel()));
        return request;
    }

    // ---------------------------------------------------------------------------------------------

    private ApprovalStep checkCanDecide(ApprovalRequest request, AppUserPrincipal me) {
        ApprovalStep step = request.getWaitingStep()
                .orElseThrow(() -> new BusinessException("This request is no longer pending."));
        if (!me.hasPermission(LifecyclePermissions.APPROVAL_DECIDE)) {
            throw new BusinessException("You cannot decide approvals.");
        }
        if (request.getRequestedById().equals(me.getId())) {
            throw new BusinessException("You cannot approve or reject your own request.");
        }
        if (request.decidedBy(me.getId())) {
            throw new BusinessException("You already approved an earlier step of this request. Another person must decide this step.");
        }
        if (!step.getRoleId().equals(myRoleId(me))) {
            throw new BusinessException("This step must be decided by the " + step.getRoleName() + " role.");
        }
        return step;
    }

    private static void checkComment(String comment, boolean required) {
        if (required && (comment == null || comment.isBlank())) {
            throw new BusinessException("Enter the reason for rejecting.");
        }
        if (comment != null && comment.length() > 500) {
            throw new BusinessException("The comment must be at most 500 characters.");
        }
    }

    private Long myRoleId(AppUserPrincipal me) {
        return roleRepository.findByCompanyIdAndCodeIgnoreCase(me.getCompanyId(), me.getRoleCode())
                .orElseThrow(() -> new BusinessException("Your role was not found."))
                .getId();
    }
}
