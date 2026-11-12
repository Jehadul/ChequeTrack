package com.chequetrack.report.definition;

import com.chequetrack.audit.domain.AuditLogEntry;
import com.chequetrack.audit.domain.StatusHistoryEntry;
import com.chequetrack.common.exception.BusinessException;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Component
public class ChequeHistoryReport implements ReportDefinition {

    private static final int MAX_CHEQUES = 10;

    private final EntityManager em;

    public ChequeHistoryReport(EntityManager em) {
        this.em = em;
    }

    @Override
    public String code() {
        return "history";
    }

    @Override
    public String title() {
        return "Cheque history";
    }

    @Override
    public String description() {
        return "The full story of one cheque: every status change with its reason, and every action on it, with who and when.";
    }

    @Override
    public Set<String> permissions() {
        return Set.of(ReceivedPermissions.RECEIVED_VIEW, IssuedPermissions.ISSUED_VIEW);
    }

    @Override
    public Set<ReportFilter.Field> filters() {
        return EnumSet.of(ReportFilter.Field.CHEQUE);
    }

    private record Event(Instant at, String cheque, String event, String details, String user) {
    }

    @Override
    public ReportTable run(ReportFilter f, String filterText) {
        boolean issued = "ISSUED".equals(f.getChequeKind());
        String number = f.getChequeNumber() == null ? "" : f.getChequeNumber().trim();
        ReportTable.Builder t = ReportTable.builder(title(), (issued ? "Issued" : "Received") + " cheque " + (number.isEmpty() ? "–" : number))
                .column(Column.timestamp("When"))
                .column(Column.text("Cheque", 14))
                .column(Column.text("What happened", 20))
                .column(Column.text("Details", 40))
                .column(Column.text("By", 9));
        if (number.isEmpty()) {
            return t.note("Enter the cheque number and press Show.").build();
        }
        String permission = issued ? IssuedPermissions.ISSUED_VIEW : ReceivedPermissions.RECEIVED_VIEW;
        if (!CurrentUser.get().hasPermission(permission)) {
            throw new BusinessException("Your role cannot see " + (issued ? "issued" : "received") + " cheques.");
        }
        String type = issued ? IssuedCheque.RECORD_TYPE : ReceivedCheque.RECORD_TYPE;
        // the branch filter limits this to cheques the user may see
        List<Object[]> cheques = em.createQuery("select c.id, c.chequeNumber, c.party.name from " + type
                        + " c where lower(c.chequeNumber) = lower(:n) order by c.id", Object[].class)
                .setParameter("n", number).setMaxResults(MAX_CHEQUES).getResultList();
        if (cheques.isEmpty()) {
            return t.note("No " + (issued ? "issued" : "received") + " cheque numbered " + number + " in your branches.").build();
        }
        Long companyId = CurrentUser.get().getCompanyId();
        List<Event> events = new ArrayList<>();
        for (Object[] c : cheques) {
            Long id = (Long) c[0];
            String label = c[1] + " · " + c[2];
            for (StatusHistoryEntry h : em.createQuery("select h from StatusHistoryEntry h where h.companyId = :co "
                            + "and h.entityType = :type and h.entityId = :id", StatusHistoryEntry.class)
                    .setParameter("co", companyId).setParameter("type", type).setParameter("id", id).getResultList()) {
                String what = h.getFromStatus() == null ? "Entered as " + status(issued, h.getToStatus())
                        : status(issued, h.getFromStatus()) + " → " + status(issued, h.getToStatus());
                events.add(new Event(h.getChangedAt(), label, what, h.getReason(), h.getUsername()));
            }
            for (AuditLogEntry a : em.createQuery("select a from AuditLogEntry a where a.companyId = :co "
                            + "and a.entityType = :type and a.entityId = :id", AuditLogEntry.class)
                    .setParameter("co", companyId).setParameter("type", type).setParameter("id", String.valueOf(id)).getResultList()) {
                if (a.getAction().startsWith("CHEQUE_")) {
                    continue;  // status changes are already listed, with their reason
                }
                events.add(new Event(a.getOccurredAt(), label, action(a.getAction()), a.getSummary(), a.getUsername()));
            }
        }
        events.sort(Comparator.comparing(Event::at));
        for (Event e : events) {
            t.row(e.at(), e.cheque(), e.event(), e.details(), e.user());
        }
        if (cheques.size() > 1) {
            t.note(cheques.size() + " cheques carry this number (different banks or accounts); all are listed.");
        }
        return t.totals(events.size() + " event(s)", null, null, null, null).build();
    }

    private static String status(boolean issued, String s) {
        try {
            return issued ? IssuedStatus.valueOf(s).getLabel() : ReceivedStatus.valueOf(s).getLabel();
        } catch (IllegalArgumentException ex) {
            return s;
        }
    }

    /** "APPROVAL_REQUESTED" → "Approval requested" */
    private static String action(String code) {
        String s = code.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
