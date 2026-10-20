package com.chequetrack.lifecycle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One approval needed by a request, copied from the approval rules when the request was made. */
@Getter
@Entity
@Table(name = "approval_step")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false, updatable = false)
    private ApprovalRequest request;

    @Column(name = "step_order", nullable = false, updatable = false)
    private int stepOrder;

    @Column(name = "role_id", nullable = false, updatable = false)
    private Long roleId;

    @Column(name = "role_name", nullable = false, length = 100, updatable = false)
    private String roleName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StepStatus status = StepStatus.WAITING;

    @Column(name = "decided_by_id")
    private Long decidedById;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(length = 500)
    private String comment;

    ApprovalStep(ApprovalRequest request, int stepOrder, Long roleId, String roleName) {
        this.request = request;
        this.stepOrder = stepOrder;
        this.roleId = roleId;
        this.roleName = roleName;
    }

    void decide(StepStatus decision, Long userId, String username, String comment, Instant at) {
        this.status = decision;
        this.decidedById = userId;
        this.decidedBy = username;
        this.comment = comment;
        this.decidedAt = at;
    }

    void skip() {
        if (status == StepStatus.WAITING) {
            this.status = StepStatus.NOT_NEEDED;
        }
    }
}
