package com.chequetrack.report.definition;

import com.chequetrack.issued.IssuedPermissions;
import com.chequetrack.printing.domain.PrintLog;
import com.chequetrack.report.model.ReportFilter;
import com.chequetrack.report.model.ReportTable;
import com.chequetrack.report.model.ReportTable.Column;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PrintLogReport implements ReportDefinition {

    private final EntityManager em;
    private final ReportSupport support;
    private final Clock clock;

    public PrintLogReport(EntityManager em, ReportSupport support, Clock clock) {
        this.em = em;
        this.support = support;
        this.clock = clock;
    }

    @Override
    public String code() {
        return "print-log";
    }

    @Override
    public String title() {
        return "Print log";
    }

    @Override
    public String description() {
        return "Every cheque print, reprint, spoiled leaf and test print: who printed, on which printer, and how it came out.";
    }

    @Override
    public Set<String> permissions() {
        return Set.of(IssuedPermissions.ISSUED_VIEW);
    }

    @Override
    public Set<ReportFilter.Field> filters() {
        return EnumSet.of(ReportFilter.Field.DATE, ReportFilter.Field.BRANCH, ReportFilter.Field.STATUS);
    }

    @Override
    public String dateLabel() {
        return "Printed";
    }

    @Override
    public List<String[]> statuses() {
        return List.of(new String[] {"OK", "Printed"}, new String[] {"PENDING", "Waiting for confirmation"},
                new String[] {"SPOILED", "Spoiled, leaf void"}, new String[] {"SUPERSEDED", "Printed again"},
                new String[] {"TEST", "Test print"});
    }

    @Override
    public ReportTable run(ReportFilter f, String filterText) {
        List<PrintLog> list = new Query("select p from PrintLog p")
                .when(f.getFrom() == null ? null : f.getFrom().atStartOfDay(clock.getZone()).toInstant(), "p.createdAt >= :from", "from")
                .when(f.getTo() == null ? null : f.getTo().plusDays(1).atStartOfDay(clock.getZone()).toInstant(), "p.createdAt < :to", "to")
                .when(f.getBranchId(), "p.branchId = :branch", "branch")
                .when(ReportSupport.status(f.getStatus(), PrintLog.Outcome.class), "p.outcome = :outcome", "outcome")
                .orderBy("p.id desc")
                .list(em, PrintLog.class, MAX_ROWS + 1);
        Map<Long, String> branches = support.branchCodes();
        ReportTable.Builder t = ReportTable.builder(title(), filterText)
                .column(Column.timestamp("Printed"))
                .column(Column.text("Kind", 6))
                .column(Column.text("Leaf", 10))
                .column(Column.text("Printer", 12))
                .column(Column.text("Result", 12))
                .column(Column.text("Note", 18))
                .column(Column.text("By", 8))
                .column(Column.text("Confirmed by", 16))
                .column(Column.text("Branch", 5));
        int n = 0;
        for (PrintLog p : list) {
            if (n++ == MAX_ROWS) {
                break;
            }
            String confirmed = p.getDecidedBy() == null ? null : p.getDecidedBy() + ", " + support.timestamp(p.getDecidedAt());
            t.row(p.getCreatedAt(), label(p.getKind().name()), p.getChequeNumber() == null ? "sample (test)" : p.getChequeNumber(),
                    p.getPrinterName(), outcome(p.getOutcome()), p.getNote(), p.getCreatedBy(), confirmed, branches.get(p.getBranchId()));
        }
        return t.totals(Math.min(list.size(), MAX_ROWS) + " print(s)", null, null, null, null, null, null, null, null)
                .truncated(list.size() > MAX_ROWS).build();
    }

    private static String label(String s) {
        return s.charAt(0) + s.substring(1).toLowerCase();
    }

    private static String outcome(PrintLog.Outcome o) {
        return switch (o) {
            case OK -> "Printed";
            case PENDING -> "Waiting";
            case SPOILED -> "Spoiled, leaf void";
            case SUPERSEDED -> "Printed again";
            case TEST -> "Test print";
        };
    }
}
