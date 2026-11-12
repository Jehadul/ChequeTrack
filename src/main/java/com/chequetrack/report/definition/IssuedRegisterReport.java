package com.chequetrack.report.definition;

import com.chequetrack.issued.IssuedPermissions;
import com.chequetrack.issued.domain.IssuedCheque;
import com.chequetrack.lifecycle.domain.IssuedStatus;
import com.chequetrack.report.model.ReportFilter;
import com.chequetrack.report.model.ReportTable;
import com.chequetrack.report.model.ReportTable.Column;
import com.chequetrack.setup.domain.PartyType;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class IssuedRegisterReport implements ReportDefinition {

    private final EntityManager em;
    private final ReportSupport support;

    public IssuedRegisterReport(EntityManager em, ReportSupport support) {
        this.em = em;
        this.support = support;
    }

    @Override
    public String code() {
        return "issued";
    }

    @Override
    public String title() {
        return "Issued cheque register";
    }

    @Override
    public String description() {
        return "Every cheque written to suppliers: account, payee, status and hand-over.";
    }

    @Override
    public Set<String> permissions() {
        return Set.of(IssuedPermissions.ISSUED_VIEW);
    }

    @Override
    public Set<ReportFilter.Field> filters() {
        return EnumSet.of(ReportFilter.Field.DATE, ReportFilter.Field.BRANCH, ReportFilter.Field.BANK,
                ReportFilter.Field.STATUS, ReportFilter.Field.PARTY);
    }

    @Override
    public String dateLabel() {
        return "Cheque date";
    }

    @Override
    public List<String[]> statuses() {
        return ReportSupport.statusOptions(IssuedStatus.class);
    }

    @Override
    public PartyType partyType() {
        return PartyType.SUPPLIER;
    }

    @Override
    public ReportTable run(ReportFilter f, String filterText) {
        List<IssuedCheque> list = new Query("select c from IssuedCheque c join fetch c.party join fetch c.account a "
                + "join fetch a.bankBranch bb join fetch bb.bank")
                .when(f.getFrom(), "c.chequeDate >= :from", "from")
                .when(f.getTo(), "c.chequeDate <= :to", "to")
                .when(f.getBranchId(), "c.branchId = :branch", "branch")
                .when(f.getBankId(), "bb.bank.id = :bank", "bank")
                .when(ReportSupport.status(f.getStatus(), IssuedStatus.class), "c.status = :status", "status")
                .when(f.getPartyId(), "c.party.id = :party", "party")
                .orderBy("c.chequeDate, c.chequeNumber")
                .list(em, IssuedCheque.class, MAX_ROWS + 1);
        Map<Long, String> branches = support.branchCodes();
        ReportTable.Builder t = ReportTable.builder(title(), filterText)
                .column(Column.date("Cheque date"))
                .column(Column.text("Cheque no.", 10))
                .column(Column.text("Pay to", 18))
                .column(Column.text("Account", 14))
                .column(Column.text("Crossing", 8))
                .column(Column.text("Status", 9))
                .column(Column.text("Handed over", 14))
                .column(Column.text("Branch", 5))
                .column(Column.amount("Amount (Tk)"));
        BigDecimal total = BigDecimal.ZERO;
        int n = 0;
        for (IssuedCheque c : list) {
            if (n++ == MAX_ROWS) {
                break;
            }
            String handover = c.getHandedOverTo() == null ? null : c.getHandedOverTo() + ", " + support.date(c.getHandedOverOn());
            t.row(c.getChequeDate(), c.getChequeNumber(), c.getPayeeName(), c.getAccount().getLabel(),
                    c.getCrossing().getLabel(), c.getStatus().getLabel(), handover, branches.get(c.getBranchId()), c.getAmount());
            total = total.add(c.getAmount());
        }
        return t.totals(Math.min(list.size(), MAX_ROWS) + " cheque(s)", null, null, null, null, null, null, null, total)
                .truncated(list.size() > MAX_ROWS).build();
    }
}
