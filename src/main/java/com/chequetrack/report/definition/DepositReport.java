package com.chequetrack.report.definition;

import com.chequetrack.received.ReceivedPermissions;
import com.chequetrack.received.domain.DepositBatch;
import com.chequetrack.report.model.ReportFilter;
import com.chequetrack.report.model.ReportTable;
import com.chequetrack.report.model.ReportTable.Column;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DepositReport implements ReportDefinition {

    private final EntityManager em;
    private final ReportSupport support;

    public DepositReport(EntityManager em, ReportSupport support) {
        this.em = em;
        this.support = support;
    }

    @Override
    public String code() {
        return "deposits";
    }

    @Override
    public String title() {
        return "Deposit slips";
    }

    @Override
    public String description() {
        return "Every deposit batch taken to the bank: date, account, number of cheques and total. Each slip prints from its deposit page.";
    }

    @Override
    public Set<String> permissions() {
        return Set.of(ReceivedPermissions.RECEIVED_VIEW);
    }

    @Override
    public Set<ReportFilter.Field> filters() {
        return EnumSet.of(ReportFilter.Field.DATE, ReportFilter.Field.BRANCH, ReportFilter.Field.BANK);
    }

    @Override
    public String dateLabel() {
        return "Deposit date";
    }

    @Override
    public ReportTable run(ReportFilter f, String filterText) {
        List<DepositBatch> list = new Query("select b from DepositBatch b join fetch b.account a join fetch a.bankBranch bb join fetch bb.bank")
                .when(f.getFrom(), "b.depositDate >= :from", "from")
                .when(f.getTo(), "b.depositDate <= :to", "to")
                .when(f.getBranchId(), "b.branchId = :branch", "branch")
                .when(f.getBankId(), "bb.bank.id = :bank", "bank")
                .orderBy("b.depositDate desc, b.number desc")
                .list(em, DepositBatch.class, MAX_ROWS + 1);
        Map<Long, String> branches = support.branchCodes();
        ReportTable.Builder t = ReportTable.builder(title(), filterText)
                .column(Column.date("Deposit date"))
                .column(Column.text("Slip no.", 12))
                .column(Column.text("Deposited into", 22))
                .column(Column.number("Cheques"))
                .column(Column.text("Notes", 20))
                .column(Column.text("Prepared by", 10))
                .column(Column.text("Branch", 5))
                .column(Column.amount("Total (Tk)"));
        BigDecimal total = BigDecimal.ZERO;
        long cheques = 0;
        int n = 0;
        for (DepositBatch b : list) {
            if (n++ == MAX_ROWS) {
                break;
            }
            t.row(b.getDepositDate(), b.getNumber(), b.getAccount().getLabel(), b.getChequeCount(), b.getNotes(),
                    b.getCreatedBy(), branches.get(b.getBranchId()), b.getTotalAmount());
            total = total.add(b.getTotalAmount());
            cheques += b.getChequeCount();
        }
        return t.totals(Math.min(list.size(), MAX_ROWS) + " slip(s)", null, null, cheques, null, null, null, total)
                .truncated(list.size() > MAX_ROWS).build();
    }
}
