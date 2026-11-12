package com.chequetrack.report.definition;

import com.chequetrack.chequebook.ChequeBookPermissions;
import com.chequetrack.chequebook.domain.BookStatus;
import com.chequetrack.chequebook.domain.ChequeBook;
import com.chequetrack.chequebook.service.ChequeBookService;
import com.chequetrack.report.model.ReportFilter;
import com.chequetrack.report.model.ReportTable;
import com.chequetrack.report.model.ReportTable.Column;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class StockReport implements ReportDefinition {

    private final ChequeBookService bookService;
    private final ReportSupport support;

    public StockReport(ChequeBookService bookService, ReportSupport support) {
        this.bookService = bookService;
        this.support = support;
    }

    @Override
    public String code() {
        return "stock";
    }

    @Override
    public String title() {
        return "Cheque book stock";
    }

    @Override
    public String description() {
        return "Every cheque book with its unused, in-use, used, void and closed leaves, per bank account.";
    }

    @Override
    public Set<String> permissions() {
        return Set.of(ChequeBookPermissions.CHEQUEBOOK_VIEW);
    }

    @Override
    public Set<ReportFilter.Field> filters() {
        return EnumSet.of(ReportFilter.Field.BRANCH, ReportFilter.Field.BANK, ReportFilter.Field.STATUS);
    }

    @Override
    public List<String[]> statuses() {
        return List.of(new String[] {"ACTIVE", "Active"}, new String[] {"FINISHED", "Finished"},
                new String[] {"CANCELLED", "Cancelled"}, new String[] {"LOST", "Lost"});
    }

    @Override
    public ReportTable run(ReportFilter f, String filterText) {
        Map<Long, String> branches = support.branchCodes();
        ReportTable.Builder t = ReportTable.builder(title(), filterText)
                .column(Column.text("Bank account", 20))
                .column(Column.text("Leaves", 16))
                .column(Column.text("Book", 7))
                .column(Column.number("Unused"))
                .column(Column.number("In use"))
                .column(Column.number("Used"))
                .column(Column.number("Void"))
                .column(Column.number("Closed"))
                .column(Column.date("Received"))
                .column(Column.text("Branch", 5));
        long[] sum = new long[5];
        int books = 0;
        for (ChequeBookService.BookRow r : bookService.search(null, ReportSupport.status(f.getStatus(), BookStatus.class))) {
            ChequeBook b = r.book();
            if (f.getBranchId() != null && !f.getBranchId().equals(b.getBranchId())
                    || f.getBankId() != null && !f.getBankId().equals(b.getAccount().getBankBranch().getBank().getId())) {
                continue;
            }
            long[] c = {r.count("UNUSED"), r.count("ALLOCATED"), r.count("USED"), r.count("VOID"),
                    r.count("CANCELLED") + r.count("LOST")};
            for (int i = 0; i < c.length; i++) {
                sum[i] += c[i];
            }
            books++;
            t.row(b.getAccount().getLabel(), b.getRangeLabel(), b.getStatus().name().charAt(0) + b.getStatus().name().substring(1).toLowerCase(),
                    c[0], c[1], c[2], c[3], c[4], b.getReceivedDate(), branches.get(b.getBranchId()));
        }
        t.note("In use: taken by a cheque that is not printed yet. Closed: left blank in a book that was cancelled or lost.");
        return t.totals(books + " book(s)", null, null, sum[0], sum[1], sum[2], sum[3], sum[4], null, null).build();
    }
}
