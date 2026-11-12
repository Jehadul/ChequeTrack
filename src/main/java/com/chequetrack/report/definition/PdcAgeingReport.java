package com.chequetrack.report.definition;

import com.chequetrack.issued.IssuedPermissions;
import com.chequetrack.issued.domain.IssuedCheque;
import com.chequetrack.lifecycle.domain.IssuedStatus;
import com.chequetrack.lifecycle.domain.ReceivedStatus;
import com.chequetrack.received.ReceivedPermissions;
import com.chequetrack.received.domain.ReceivedCheque;
import com.chequetrack.report.model.ReportFilter;
import com.chequetrack.report.model.ReportTable;
import com.chequetrack.report.model.ReportTable.Column;
import com.chequetrack.security.service.CurrentUser;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PdcAgeingReport implements ReportDefinition {

    private static final String[] BUCKETS = {"Due in 0–30 days", "31–60 days", "61–90 days", "Over 90 days"};

    private final EntityManager em;
    private final ReportSupport support;
    private final Clock clock;

    public PdcAgeingReport(EntityManager em, ReportSupport support, Clock clock) {
        this.em = em;
        this.support = support;
        this.clock = clock;
    }

    @Override
    public String code() {
        return "pdc-ageing";
    }

    @Override
    public String title() {
        return "PDC ageing";
    }

    @Override
    public String description() {
        return "Post-dated cheques still to come in and go out, grouped by days until they are due (0–30, 31–60, 61–90, 90+).";
    }

    @Override
    public Set<String> permissions() {
        return Set.of(ReceivedPermissions.RECEIVED_VIEW, IssuedPermissions.ISSUED_VIEW);
    }

    @Override
    public Set<ReportFilter.Field> filters() {
        return EnumSet.of(ReportFilter.Field.DATE, ReportFilter.Field.BRANCH, ReportFilter.Field.BANK,
                ReportFilter.Field.STATUS, ReportFilter.Field.PARTY);
    }

    @Override
    public String dateLabel() {
        return "Due date";
    }

    @Override
    public List<String[]> statuses() {
        return List.of(new String[] {"IN", "Received (money in)"}, new String[] {"OUT", "Issued (money out)"});
    }

    private record Line(String direction, LocalDate date, String number, String party, String bank, BigDecimal amount,
                        Long branchId, int deferCount) {
    }

    @Override
    public ReportTable run(ReportFilter f, String filterText) {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = f.getFrom() == null || f.getFrom().isBefore(today.plusDays(1)) ? today.plusDays(1) : f.getFrom();
        List<Line> lines = new ArrayList<>();
        boolean in = !"OUT".equals(f.getStatus()) && CurrentUser.get().hasPermission(ReceivedPermissions.RECEIVED_VIEW);
        boolean out = !"IN".equals(f.getStatus()) && CurrentUser.get().hasPermission(IssuedPermissions.ISSUED_VIEW);
        if (in) {
            for (ReceivedCheque c : new Query("select c from ReceivedCheque c join fetch c.party join fetch c.drawerBank")
                    .where("c.status = :st").param("st", ReceivedStatus.IN_HAND)
                    .where("c.chequeDate >= :from").param("from", from)
                    .when(f.getTo(), "c.chequeDate <= :to", "to")
                    .when(f.getBranchId(), "c.branchId = :branch", "branch")
                    .when(f.getBankId(), "c.drawerBank.id = :bank", "bank")
                    .when(f.getPartyId(), "c.party.id = :party", "party")
                    .list(em, ReceivedCheque.class, MAX_ROWS)) {
                lines.add(new Line("In", c.getChequeDate(), c.getChequeNumber(), c.getParty().getName(),
                        c.getDrawerBank().getName(), c.getAmount(), c.getBranchId(), c.getDeferCount()));
            }
        }
        if (out) {
            for (IssuedCheque c : new Query("select c from IssuedCheque c join fetch c.party join fetch c.account a "
                    + "join fetch a.bankBranch bb join fetch bb.bank")
                    .where("c.status in :st").param("st", EnumSet.of(IssuedStatus.APPROVED, IssuedStatus.PRINTED, IssuedStatus.ISSUED))
                    .where("c.chequeDate >= :from").param("from", from)
                    .when(f.getTo(), "c.chequeDate <= :to", "to")
                    .when(f.getBranchId(), "c.branchId = :branch", "branch")
                    .when(f.getBankId(), "bb.bank.id = :bank", "bank")
                    .when(f.getPartyId(), "c.party.id = :party", "party")
                    .list(em, IssuedCheque.class, MAX_ROWS)) {
                lines.add(new Line("Out", c.getChequeDate(), c.getChequeNumber(), c.getPayeeName(),
                        c.getAccount().getBankBranch().getBank().getName(), c.getAmount(), c.getBranchId(), c.getDeferCount()));
            }
        }
        lines.sort(Comparator.comparing(Line::date).thenComparing(Line::direction));

        Map<Long, String> branches = support.branchCodes();
        ReportTable.Builder t = ReportTable.builder(title(), filterText + " · as of " + support.date(today))
                .column(Column.text("Bucket", 11))
                .column(Column.date("Due date"))
                .column(Column.number("Days"))
                .column(Column.text("In / out", 6))
                .column(Column.text("Cheque no.", 10))
                .column(Column.text("Customer / payee", 18))
                .column(Column.text("Bank", 16))
                .column(Column.number("Deferred"))
                .column(Column.text("Branch", 5))
                .column(Column.amount("Amount (Tk)"));
        Map<String, BigDecimal[]> sums = new LinkedHashMap<>();
        for (String b : BUCKETS) {
            sums.put(b, new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
        }
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        int count = 0;
        for (Line l : lines) {
            if (count++ == MAX_ROWS) {
                break;
            }
            long days = ChronoUnit.DAYS.between(today, l.date());
            String bucket = days <= 30 ? BUCKETS[0] : days <= 60 ? BUCKETS[1] : days <= 90 ? BUCKETS[2] : BUCKETS[3];
            t.row(bucket, l.date(), days, l.direction(), l.number(), l.party(), l.bank(), l.deferCount(),
                    branches.get(l.branchId()), l.amount());
            boolean isIn = "In".equals(l.direction());
            sums.get(bucket)[isIn ? 0 : 1] = sums.get(bucket)[isIn ? 0 : 1].add(l.amount());
            if (isIn) {
                totalIn = totalIn.add(l.amount());
            } else {
                totalOut = totalOut.add(l.amount());
            }
        }
        for (Map.Entry<String, BigDecimal[]> e : sums.entrySet()) {
            t.note(e.getKey() + ": in Tk " + support.amount(e.getValue()[0]) + ", out Tk " + support.amount(e.getValue()[1]));
        }
        t.note("Total: in Tk " + support.amount(totalIn) + ", out Tk " + support.amount(totalOut) + ", net Tk "
                + support.amount(totalIn.subtract(totalOut)));
        return t.totals(Math.min(lines.size(), MAX_ROWS) + " cheque(s)", null, null, null, null, null, null, null, null, null)
                .truncated(lines.size() > MAX_ROWS).build();
    }
}
