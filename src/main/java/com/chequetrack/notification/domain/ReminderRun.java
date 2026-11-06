package com.chequetrack.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Entity
@Table(name = "reminder_run")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReminderRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(name = "run_date", nullable = false, updatable = false)
    private LocalDate runDate;

    @Column(name = "run_by", nullable = false, length = 100, updatable = false)
    private String runBy;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private int branches;

    @Column(nullable = false)
    private int items;

    @Column(nullable = false)
    private int emails;

    @Column(nullable = false)
    private int notifications;

    public static ReminderRun start(Long companyId, LocalDate date, String runBy, Instant now) {
        ReminderRun r = new ReminderRun();
        r.companyId = companyId;
        r.runDate = date;
        r.runBy = runBy;
        r.startedAt = now;
        return r;
    }

    public void add(int items, int emails, int notifications) {
        this.branches++;
        this.items += items;
        this.emails += emails;
        this.notifications += notifications;
    }
}
