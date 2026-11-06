package com.chequetrack.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "email_outbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEmail {

    public static final int MAX_ATTEMPTS = 5;

    public enum Status { PENDING, SENT, FAILED, NOT_SENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "branch_id", updatable = false)
    private Long branchId;

    @Column(nullable = false, length = 30, updatable = false)
    private String kind;

    @Column(name = "to_address", nullable = false, length = 150, updatable = false)
    private String toAddress;

    @Column(name = "to_name", length = 150, updatable = false)
    private String toName;

    @Column(nullable = false, length = 200, updatable = false)
    private String subject;

    @Column(name = "body_html", nullable = false, columnDefinition = "text", updatable = false)
    private String bodyHtml;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "dedup_key", length = 150, updatable = false)
    private String dedupKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    public static OutboxEmail create(Long companyId, Long branchId, String kind, String toAddress, String toName,
                                     String subject, String bodyHtml, String dedupKey, Instant now) {
        OutboxEmail e = new OutboxEmail();
        e.companyId = companyId;
        e.branchId = branchId;
        e.kind = kind;
        e.toAddress = toAddress.trim();
        e.toName = toName;
        e.subject = Notification.cut(subject, 200);
        e.bodyHtml = bodyHtml;
        e.dedupKey = dedupKey;
        e.createdAt = now;
        return e;
    }

    public void sent(Instant now) {
        this.attempts++;
        this.status = Status.SENT;
        this.sentAt = now;
        this.lastError = null;
    }

    /** A failed attempt; after MAX_ATTEMPTS the email is given up. */
    public void failed(String error) {
        this.attempts++;
        this.lastError = Notification.cut(error, 500);
        this.status = attempts >= MAX_ATTEMPTS ? Status.FAILED : Status.PENDING;
    }

    /** Mail is switched off: kept for reading on screen only. */
    public void notSent(String why) {
        this.status = Status.NOT_SENT;
        this.lastError = why;
    }
}
