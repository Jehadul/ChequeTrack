package com.chequetrack.report.definition;

import com.chequetrack.audit.domain.StatusHistoryEntry;
import com.chequetrack.chequebook.ChequeBookPermissions;
import com.chequetrack.chequebook.domain.ChequeLeaf;
import com.chequetrack.issued.IssuedPermissions;
import com.chequetrack.issued.domain.IssuedCheque;
import com.chequetrack.report.model.ReportFilter;
import com.chequetrack.report.model.ReportTable;
import com.chequetrack.report.model.ReportTable.Column;
import com.chequetrack.security.service.CurrentUser;
import com.chequetrack.setup.domain.PartyType;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class VoidStopReport implements ReportDefinition {

    private static final String LEAF = "ChequeLeaf";

    private final EntityManager em;
    private final ReportSupport support;
    private final Clock clock;

    public VoidStopReport(EntityManager em, ReportSupport support, Clock clock) {
        this.em = em;
        this.support = support;
        this.clock = clock;
    }

    @Override
    public String code() {
        return "void-stop";
    }

    @Override
    public String title() {
        return "Void and stop-payment list";
    }

    @Override
    public String description() {
        return "Cheques voided, payments stopped and leaves voided (spoiled prints, damaged leaves), with reason and user.";
    }

    @Override
    public Set<String> permissions() {
        return Set.of(IssuedPermissions.ISSUED_VIEW, ChequeBookPermissions.CHEQUEBOOK_VIEW);
    }

    @Override
    public Set<ReportFilter.Field> filters() {
        return EnumSet.of(ReportFilter.Field.DATE, ReportFilter.Field.BRANCH, ReportFilter.Field.BANK,
                ReportFilter.Field.STATUS, ReportFilter.Field.PARTY);
    }

    @Override
    public List<String[]> statuses() {
        return List.of(new String[] {"VOID", "Cheque voided"}, new String[] {"STOPPED", "Payment stopped"},
                new String[] {"LEAF", "Leaf voided"});
    }

    @Override
    public PartyType partyType() {
        return PartyType.SUPPLIER;
    }

    @Override
    public ReportTable run(ReportFilter f, String filterText) {
        boolean cheques = CurrentUser.get().hasPermission(IssuedPermissions.ISSUED_VIEW) && !"LEAF".equals(f.getStatus());
        boolean leaves = CurrentUser.get().hasPermission(ChequeBookPermissions.CHEQUEBOOK_VIEW) && f.getPartyId() == null
                && (f.getStatus() == null || f.getStatus().isBlank() || "LEAF".equals(f.getStatus()));
        Collection<Long> branchIds = support.visibleBranches(f.getBranchId());
        Query q = new Query("select h from StatusHistoryEntry h")
                .where("h.companyId = :co").param("co", CurrentUser.get().getCompanyId())
                .when(branchIds, "h.branchId in :branches", "branches")
                .when(f.getFrom() == null ? null : f.getFrom().atStartOfDay(clock.getZone()).toInstant(), "h.changedAt >= :from", "from")
                .when(f.getTo() == null ? null : f.getTo().plusDays(1).atStartOfDay(clock.getZone()).toInstant(), "h.changedAt < :to", "to")
                .orderBy("h.changedAt desc, h.id desc");
        String chequeStatus = f.getStatus() == null || f.getStatus().isBlank() || "LEAF".equals(f.getStatus()) ? null : f.getStatus();
        if (cheques && leaves) {
            q.where("((h.entityType = 'IssuedCheque' and h.toStatus in ('VOID', 'STOPPED')) or (h.entityType = 'ChequeLeaf' and h.toStatus = 'VOID'))");
        } else if (cheques) {
            q.where("h.entityType = 'IssuedCheque'").when(chequeStatus, "h.toStatus = :st", "st");
            if (chequeStatus == null) {
                q.where("h.toStatus in ('VOID', 'STOPPED')");
            }
        } else if (leaves) {
            q.where("h.entityType = 'ChequeLeaf' and h.toStatus = 'VOID'");
        } else {
            q.where("1 = 0");
        }
        List<StatusHistoryEntry> rows = q.list(em, StatusHistoryEntry.class, MAX_ROWS + 1);

        Map<Long, IssuedCheque> issued = load(IssuedCheque.class, "select c from IssuedCheque c join fetch c.party join fetch c.account a "
                + "join fetch a.bankBranch bb join fetch bb.bank where c.id in :ids", ids(rows, IssuedCheque.RECORD_TYPE), IssuedCheque::getId);
        Map<Long, ChequeLeaf> leafMap = load(ChequeLeaf.class, "select l from ChequeLeaf l join fetch l.book b join fetch b.account a "
                + "join fetch a.bankBranch bb join fetch bb.bank where l.id in :ids", ids(rows, LEAF), ChequeLeaf::getId);
        Map<Long, String> branches = support.branchCodes();
        ReportTable.Builder t = ReportTable.builder(title(), filterText)
                .column(Column.timestamp("When"))
                .column(Column.text("What", 10))
                .column(Column.text("Leaf / cheque", 10))
                .column(Column.text("Pay to", 14))
                .column(Column.text("Account", 14))
                .column(Column.text("Reason", 24))
                .column(Column.text("By", 8))
                .column(Column.text("Branch", 5))
                .column(Column.amount("Amount (Tk)"));
        BigDecimal total = BigDecimal.ZERO;
        int n = 0;
        for (StatusHistoryEntry h : rows) {
            if (n == MAX_ROWS) {
                break;
            }
            if (IssuedCheque.RECORD_TYPE.equals(h.getEntityType())) {
                IssuedCheque c = issued.get(h.getEntityId());
                if (c == null || f.getPartyId() != null && !f.getPartyId().equals(c.getParty().getId())
                        || f.getBankId() != null && !f.getBankId().equals(c.getAccount().getBankBranch().getBank().getId())) {
                    continue;
                }
                t.row(h.getChangedAt(), "STOPPED".equals(h.getToStatus()) ? "Payment stopped" : "Cheque voided", c.getChequeNumber(),
                        c.getPayeeName(), c.getAccount().getLabel(), h.getReason(), h.getUsername(), branches.get(h.getBranchId()), c.getAmount());
                total = total.add(c.getAmount());
            } else {
                ChequeLeaf l = leafMap.get(h.getEntityId());
                if (l == null || f.getBankId() != null && !f.getBankId().equals(l.getBook().getAccount().getBankBranch().getBank().getId())) {
                    continue;
                }
                t.row(h.getChangedAt(), "Leaf voided", l.getDisplayNumber(), null, l.getBook().getAccount().getLabel(),
                        h.getReason(), h.getUsername(), branches.get(h.getBranchId()), null);
            }
            n++;
        }
        return t.totals(n + " entr" + (n == 1 ? "y" : "ies"), null, null, null, null, null, null, null, total)
                .truncated(rows.size() > MAX_ROWS).build();
    }

    private <T> Map<Long, T> load(Class<T> type, String jpql, Set<Long> ids, Function<T, Long> id) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return em.createQuery(jpql, type).setParameter("ids", ids).getResultList().stream()
                .collect(Collectors.toMap(id, Function.identity()));
    }

    private static Set<Long> ids(List<StatusHistoryEntry> rows, String type) {
        return rows.stream().filter(h -> type.equals(h.getEntityType())).map(StatusHistoryEntry::getEntityId).collect(Collectors.toSet());
    }
}
