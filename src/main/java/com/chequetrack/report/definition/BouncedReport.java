package com.chequetrack.report.definition;

import com.chequetrack.lifecycle.domain.ReceivedStatus;
import com.chequetrack.received.ReceivedPermissions;
import com.chequetrack.received.domain.ReceivedCheque;
import com.chequetrack.report.model.ReportFilter;
import com.chequetrack.report.model.ReportTable;
import com.chequetrack.report.model.ReportTable.Column;
import com.chequetrack.security.service.CurrentUser;
import com.chequetrack.setup.domain.PartyType;
import com.chequetrack.setup.service.SettingsService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class BouncedReport implements ReportDefinition {

    private final EntityManager em;
    private final ReportSupport support;
    private final SettingsService settingsService;

    public BouncedReport(EntityManager em, ReportSupport support, SettingsService settingsService) {
        this.em = em;
        this.support = support;
        this.settingsService = settingsService;
    }

    @Override
    public String code() {
        return "bounced";
    }

    @Override
    public String title() {
        return "Bounced cheques";
    }

    @Override
    public String description() {
        return "Received cheques that bounced: reason, times bounced, bank charges, where they are now and the legal-notice deadline.";
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
        return "Last bounce";
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
        int noticeDays = settingsService.forCompany(CurrentUser.get().getCompanyId()).getBounceNoticeDays();
        List<ReceivedCheque> list = new Query("select c from ReceivedCheque c join fetch c.party join fetch c.drawerBank")
                .where("c.bounceCount > 0")
                .when(f.getFrom(), "c.lastBounceDate >= :from", "from")
                .when(f.getTo(), "c.lastBounceDate <= :to", "to")
                .when(f.getBranchId(), "c.branchId = :branch", "branch")
                .when(f.getBankId(), "c.drawerBank.id = :bank", "bank")
                .when(ReportSupport.status(f.getStatus(), ReceivedStatus.class), "c.status = :status", "status")
                .when(f.getPartyId(), "c.party.id = :party", "party")
                .orderBy("c.lastBounceDate desc, c.chequeNumber")
                .list(em, ReceivedCheque.class, MAX_ROWS + 1);
        Map<Long, String> branches = support.branchCodes();
        ReportTable.Builder t = ReportTable.builder(title(), filterText)
                .column(Column.date("Last bounce"))
                .column(Column.text("Cheque no.", 9))
                .column(Column.text("Customer", 16))
                .column(Column.text("Drawer bank", 14))
                .column(Column.text("Reason", 18))
                .column(Column.number("Times"))
                .column(Column.text("Status now", 9))
                .column(Column.date("Notice by"))
                .column(Column.text("Branch", 5))
                .column(Column.amount("Charges (Tk)"))
                .column(Column.amount("Amount (Tk)"));
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal charges = BigDecimal.ZERO;
        int n = 0;
        for (ReceivedCheque c : list) {
            if (n++ == MAX_ROWS) {
                break;
            }
            t.row(c.getLastBounceDate(), c.getChequeNumber(), c.getParty().getName(), c.getDrawerBank().getName(),
                    c.getLastBounceReason(), c.getBounceCount(), c.getStatus().getLabel(),
                    c.getLastBounceDate() == null ? null : c.getLastBounceDate().plusDays(noticeDays),
                    branches.get(c.getBranchId()), c.getBankCharges(), c.getAmount());
            total = total.add(c.getAmount());
            charges = charges.add(c.getBankCharges());
        }
        t.note("Legal notice: within " + noticeDays + " days of the bounce (Negotiable Instruments Act 1881, section 138), as set in Settings.");
        return t.totals(Math.min(list.size(), MAX_ROWS) + " cheque(s)", null, null, null, null, null, null, null, null, charges, total)
                .truncated(list.size() > MAX_ROWS).build();
    }
}
