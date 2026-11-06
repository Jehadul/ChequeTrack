package com.chequetrack.notification.service;

import com.chequetrack.common.web.DisplayFormat;
import com.chequetrack.issued.domain.IssuedCheque;
import com.chequetrack.lifecycle.domain.ApprovalRequest;
import com.chequetrack.lifecycle.domain.ApprovalStatus;
import com.chequetrack.lifecycle.domain.IssuedStatus;
import com.chequetrack.lifecycle.domain.ReceivedStatus;
import com.chequetrack.notification.NotificationProperties;
import com.chequetrack.received.domain.ChequeType;
import com.chequetrack.received.domain.ReceivedCheque;
import com.chequetrack.setup.domain.CompanySettings;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@Component
public class ReminderQueries {

    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final NotificationProperties properties;
    private final DisplayFormat format;

    public ReminderQueries(EntityManager em, JdbcTemplate jdbc, NotificationProperties properties, DisplayFormat format) {
        this.em = em;
        this.jdbc = jdbc;
        this.properties = properties;
        this.format = format;
    }

    public Digest digest(Recipients.BranchInfo branch, CompanySettings s, LocalDate today, Instant now) {
        Digest d = Digest.empty(branch, today, s.getReminderLeadDays());
        LocalDate until = today.plusDays(s.getReminderLeadDays());
        int validity = s.getChequeValidityDays();

        List<ReceivedCheque> open = em.createQuery("""
                        select c from ReceivedCheque c join fetch c.party
                         where c.companyId = :co and c.branchId = :br and c.status in :st
                         order by c.chequeDate, c.id
                        """, ReceivedCheque.class)
                .setParameter("co", branch.companyId()).setParameter("br", branch.id())
                .setParameter("st", EnumSet.of(ReceivedStatus.IN_HAND, ReceivedStatus.BOUNCED, ReceivedStatus.DEPOSITED))
                .getResultList();
        List<IssuedCheque> issued = em.createQuery("""
                        select c from IssuedCheque c join fetch c.party
                         where c.companyId = :co and c.branchId = :br and c.status = :st
                         order by c.chequeDate, c.id
                        """, IssuedCheque.class)
                .setParameter("co", branch.companyId()).setParameter("br", branch.id())
                .setParameter("st", IssuedStatus.ISSUED)
                .getResultList();

        // routine: what to do in the next few days
        List<Digest.Item> deposit = new ArrayList<>();
        for (ReceivedCheque c : open) {
            boolean stale = c.expiryDate(validity).isBefore(today);
            if (c.getStatus() == ReceivedStatus.IN_HAND && c.getChequeType() != ChequeType.SECURITY
                    && !c.getChequeDate().isAfter(until) && !stale) {
                deposit.add(received(c, c.getChequeDate().isAfter(today) ? "due " + format.date(c.getChequeDate()) : "can be deposited now"));
            }
        }
        d.add("to deposit", "Received cheques to deposit (dated up to " + format.date(until) + ")", false, deposit);

        int graceDays = properties.reminders().unclearedDays();
        List<Digest.Item> pay = new ArrayList<>();
        for (IssuedCheque c : issued) {
            boolean overdue = c.getChequeDate().plusDays(graceDays).isBefore(today);   // listed under "not yet cleared"
            if (!c.getChequeDate().isAfter(until) && !overdue) {
                pay.add(issued(c, c.getChequeDate().isAfter(today) ? "supplier can present it from " + format.date(c.getChequeDate())
                        : "can be presented now (dated " + format.date(c.getChequeDate()) + ")"));
            }
        }
        d.add("to be paid", "Our cheques the bank will pay soon (keep funds ready)", false, pay);

        // exceptions
        List<Digest.Item> bounced = new ArrayList<>();
        for (ReceivedCheque c : open) {
            if (c.getStatus() == ReceivedStatus.BOUNCED) {
                LocalDate notice = c.getLastBounceDate() == null ? null : c.getLastBounceDate().plusDays(s.getBounceNoticeDays());
                bounced.add(received(c, "bounced: " + c.getLastBounceReason()
                        + (notice == null ? "" : "; legal notice (NI Act s.138) by " + format.date(notice))));
            }
        }
        d.add("bounced", "Bounced cheques waiting for action", true, bounced);

        List<Digest.Item> expiring = new ArrayList<>();
        for (ReceivedCheque c : open) {
            if (c.getStatus() == ReceivedStatus.DEPOSITED) {
                continue;
            }
            long left = ChronoUnit.DAYS.between(today, c.expiryDate(validity));
            if (left <= s.getNearExpiryDays()) {
                expiring.add(received(c, left < 0 ? "STALE since " + format.date(c.expiryDate(validity)) : "expires in " + left + " day(s)"));
            }
        }
        for (IssuedCheque c : issued) {
            long left = ChronoUnit.DAYS.between(today, c.expiryDate(validity));
            if (left <= s.getNearExpiryDays()) {
                expiring.add(issued(c, left < 0 ? "our cheque is STALE; the supplier needs a new one"
                        : "our cheque expires in " + left + " day(s)"));
            }
        }
        d.add("near expiry", "Cheques close to or past their 6-month limit", true, expiring);

        List<Digest.Item> deferred = new ArrayList<>();
        for (ReceivedCheque c : open) {
            if (c.getDeferCount() >= s.getMaxDefers()) {
                deferred.add(received(c, "deferred " + c.getDeferCount() + "× (limit " + s.getMaxDefers() + ")"));
            }
        }
        for (IssuedCheque c : issued) {
            if (c.getDeferCount() >= s.getMaxDefers()) {
                deferred.add(issued(c, "deferred " + c.getDeferCount() + "× (limit " + s.getMaxDefers() + ")"));
            }
        }
        d.add("deferred too often", "Cheques deferred as often as allowed", true, deferred);

        List<Digest.Item> uncleared = new ArrayList<>();
        for (IssuedCheque c : issued) {
            if (c.getChequeDate().plusDays(graceDays).isBefore(today)) {
                uncleared.add(issued(c, "dated " + format.date(c.getChequeDate()) + ", not cleared after "
                        + ChronoUnit.DAYS.between(c.getChequeDate(), today) + " days: check the bank statement"));
            }
        }
        d.add("not yet cleared", "Our cheques not cleared " + graceDays + "+ days after their date", true, uncleared);

        d.add("low stock", "Cheque books running low", true, lowStock(branch, s.getLowStockLeaves()));
        d.add("waiting approvals", "Approvals waiting more than " + properties.reminders().approvalPendingHours() + " hours",
                true, pendingApprovals(branch, now));
        return d;
    }

    private List<Digest.Item> lowStock(Recipients.BranchInfo branch, int level) {
        return jdbc.query("""
                        select a.id, bk.code || ' ' || a.account_number, count(l.id)
                          from company_bank_account a
                          join bank_branch bb on bb.id = a.bank_branch_id
                          join bank bk on bk.id = bb.bank_id
                          left join cheque_book cb on cb.account_id = a.id and cb.status = 'ACTIVE' and cb.deleted = false
                          left join cheque_leaf l on l.book_id = cb.id and l.status = 'UNUSED' and l.deleted = false
                         where a.company_id = ? and a.branch_id = ? and a.active and a.deleted = false
                           and exists (select 1 from cheque_book x where x.account_id = a.id and x.deleted = false)
                         group by a.id, bk.code, a.account_number
                        having count(l.id) < ?
                         order by 2
                        """,
                (rs, i) -> new Digest.Item(rs.getString(2), null, null, null,
                        rs.getLong(3) + " unused leaves left (alert below " + level + ")", properties.link("/chequebooks/stock")),
                branch.companyId(), branch.id(), level);
    }

    private List<Digest.Item> pendingApprovals(Recipients.BranchInfo branch, Instant now) {
        Instant before = now.minus(properties.reminders().approvalPendingHours(), ChronoUnit.HOURS);
        List<ApprovalRequest> list = em.createQuery("""
                        select r from ApprovalRequest r
                         where r.companyId = :co and r.branchId = :br and r.status = :st and r.requestedAt < :before
                         order by r.requestedAt
                        """, ApprovalRequest.class)
                .setParameter("co", branch.companyId()).setParameter("br", branch.id())
                .setParameter("st", ApprovalStatus.PENDING).setParameter("before", before)
                .getResultList();
        List<Digest.Item> items = new ArrayList<>();
        for (ApprovalRequest r : list) {
            String waiting = r.getWaitingStep().map(st -> "waiting for " + st.getRoleName()).orElse("waiting");
            items.add(new Digest.Item(r.getApprovalAction().getLabel(), r.getRecordLabel(), r.getAmount(), null,
                    "asked by " + r.getRequestedBy() + " " + ChronoUnit.HOURS.between(r.getRequestedAt(), now) + " h ago, " + waiting,
                    properties.link("/approvals/" + r.getId())));
        }
        return items;
    }

    private Digest.Item received(ReceivedCheque c, String note) {
        return new Digest.Item(c.getChequeNumber(), c.getParty().getName(), c.getAmount(), c.getChequeDate(), note,
                properties.link("/received/" + c.getId()));
    }

    private Digest.Item issued(IssuedCheque c, String note) {
        return new Digest.Item(c.getChequeNumber(), c.getPayeeName(), c.getAmount(), c.getChequeDate(), note,
                properties.link("/issued/" + c.getId()));
    }
}
