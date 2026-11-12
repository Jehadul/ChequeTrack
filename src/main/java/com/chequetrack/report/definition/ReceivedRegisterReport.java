package com.chequetrack.report.definition;

import com.chequetrack.lifecycle.domain.ReceivedStatus;
import com.chequetrack.received.ReceivedPermissions;
import com.chequetrack.received.domain.ReceivedCheque;
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
public class ReceivedRegisterReport implements ReportDefinition {

    private final EntityManager em;
    private final ReportSupport support;

    public ReceivedRegisterReport(EntityManager em, ReportSupport support) {
        this.em = em;
        this.support = support;
    }

    @Override
    public String code() {
        return "received";
    }

    @Override
    public String title() {
        return "Received cheque register";
    }

    @Override
    public String description() {
        return "Every cheque received from customers, with its drawer bank and current status.";
    }

    @Override
    public Set<String> permissions() {
        return Set.of(ReceivedPermissions.RECEIVED_VIEW);
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
        return ReportSupport.statusOptions(ReceivedStatus.class);
    }

    @Override
    public PartyType partyType() {
        return PartyType.CUSTOMER;
    }

    @Override
    public ReportTable run(ReportFilter f, String filterText) {
        List<ReceivedCheque> list = new Query("select c from ReceivedCheque c join fetch c.party join fetch c.drawerBankBranch bb join fetch bb.bank")
                .when(f.getFrom(), "c.chequeDate >= :from", "from")
                .when(f.getTo(), "c.chequeDate <= :to", "to")
                .when(f.getBranchId(), "c.branchId = :branch", "branch")
                .when(f.getBankId(), "c.drawerBank.id = :bank", "bank")
                .when(ReportSupport.status(f.getStatus(), ReceivedStatus.class), "c.status = :status", "status")
                .when(f.getPartyId(), "c.party.id = :party", "party")
                .orderBy("c.chequeDate, c.chequeNumber")
                .list(em, ReceivedCheque.class, MAX_ROWS + 1);
        Map<Long, String> branches = support.branchCodes();
        ReportTable.Builder t = ReportTable.builder(title(), filterText)
                .column(Column.date("Cheque date"))
                .column(Column.text("Cheque no.", 9))
                .column(Column.text("Customer", 18))
                .column(Column.text("Drawer bank, branch", 22))
                .column(Column.text("Type", 8))
                .column(Column.date("Received"))
                .column(Column.text("Status", 9))
                .column(Column.text("Branch", 5))
                .column(Column.amount("Amount (Tk)"));
        BigDecimal total = BigDecimal.ZERO;
        int n = 0;
        for (ReceivedCheque c : list) {
            if (n++ == MAX_ROWS) {
                break;
            }
            t.row(c.getChequeDate(), c.getChequeNumber(), c.getParty().getName(), c.getDrawerBankBranch().getLabel(),
                    c.getChequeType().getLabel(), c.getReceivedDate(), c.getStatus().getLabel(), branches.get(c.getBranchId()),
                    c.getAmount());
            total = total.add(c.getAmount());
        }
        int shown = Math.min(list.size(), MAX_ROWS);
        return t.totals(shown + " cheque(s)", null, null, null, null, null, null, null, total)
                .truncated(list.size() > MAX_ROWS).build();
    }
}
