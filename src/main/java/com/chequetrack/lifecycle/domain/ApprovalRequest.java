package com.chequetrack.lifecycle.domain;

import com.chequetrack.common.domain.Text;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.organization.scope.BranchScopedEntity;
import com.chequetrack.setup.domain.ApprovalAction;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A pending status change that needs approval, with the approval steps it needs.
 * When the last step is approved, the change itself is carried out (TransitionService).
 * Branch-scoped with the branch of the record.
 */
@Getter
@Entity
@Table(name = "approval_request")
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalRequest extends BranchScopedEntity {

    @Column(name = "record_type", nullable = false, length = 50, updatable = false)
    private String recordType;

    @Column(name = "record_id", nullable = false, updatable = false)
    private Long recordId;

    @Column(name = "record_label", nullable = false, length = 200, updatable = false)
    private String recordLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_action", nullable = false, length = 20, updatable = false)
    private ApprovalAction approvalAction;

    @Column(name = "transition_action", nullable = false, length = 30, updatable = false)
    private String transitionAction;

    @Column(nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(length = 500, updatable = false)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "current_step", nullable = false)
    private int currentStep;

    @Column(name = "requested_by_id", nullable = false, updatable = false)
    private Long requestedById;

    @Column(name = "requested_by", nullable = false, length = 100, updatable = false)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder")
    private List<ApprovalStep> steps = new ArrayList<>();

    public static ApprovalRequest create(Long companyId, Long branchId, String recordType, Long recordId,
                                         String recordLabel, ApprovalAction approvalAction, String transitionAction,
                                         BigDecimal amount, String reason, Map<String, Object> payload,
                                         Long requestedById, String requestedBy, Instant now) {
        ApprovalRequest r = new ApprovalRequest();
        r.assignScope(companyId, branchId);
        r.recordType = recordType;
        r.recordId = recordId;
        r.recordLabel = recordLabel.length() > 200 ? recordLabel.substring(0, 200) : recordLabel;
        r.approvalAction = approvalAction;
        r.transitionAction = transitionAction;
        r.amount = amount;
        r.reason = Text.clean(reason);
        r.payload = payload == null || payload.isEmpty() ? null : payload;
        r.requestedById = requestedById;
        r.requestedBy = requestedBy;
        r.requestedAt = now;
        return r;
    }

    public void addStep(int stepOrder, Long roleId, String roleName) {
        steps.add(new ApprovalStep(this, stepOrder, roleId, roleName));
        if (steps.size() == 1) {
            currentStep = stepOrder;
        }
    }

    public Optional<ApprovalStep> getWaitingStep() {
        return status == ApprovalStatus.PENDING
                ? steps.stream().filter(s -> s.getStatus() == StepStatus.WAITING).findFirst()
                : Optional.empty();
    }

    /** True when someone approved an earlier step of this request. */
    public boolean decidedBy(Long userId) {
        return steps.stream().anyMatch(s -> userId.equals(s.getDecidedById()));
    }

    /**
     * Records an approval of the waiting step.
     * @return true when this was the last step (the change can now be carried out)
     */
    public boolean approveStep(Long userId, String username, String comment, Instant now) {
        ApprovalStep step = getWaitingStep().orElseThrow(() -> new BusinessException("This request is no longer pending."));
        step.decide(StepStatus.APPROVED, userId, username, Text.clean(comment), now);
        Optional<ApprovalStep> next = getWaitingStep();
        if (next.isPresent()) {
            currentStep = next.get().getStepOrder();
            return false;
        }
        status = ApprovalStatus.APPROVED;
        closedAt = now;
        return true;
    }

    public void reject(Long userId, String username, String comment, Instant now) {
        ApprovalStep step = getWaitingStep().orElseThrow(() -> new BusinessException("This request is no longer pending."));
        step.decide(StepStatus.REJECTED, userId, username, comment, now);
        steps.forEach(ApprovalStep::skip);
        status = ApprovalStatus.REJECTED;
        closedAt = now;
    }

    public void withdraw(Instant now) {
        if (status != ApprovalStatus.PENDING) {
            throw new BusinessException("This request is no longer pending.");
        }
        steps.forEach(ApprovalStep::skip);
        status = ApprovalStatus.WITHDRAWN;
        closedAt = now;
    }
}
