package com.chequetrack.printing.domain;

import com.chequetrack.common.domain.Text;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.organization.scope.BranchScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

@Getter
@Entity
@Table(name = "print_log")
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrintLog extends BranchScopedEntity {

    public enum Kind { PRINT, REPRINT, TEST }

    public enum Outcome {
        /** PDF produced, waiting for the user to say whether the leaf printed correctly. */
        PENDING,
        OK,
        /** The print came out wrong; the leaf was voided. */
        SPOILED,
        /** The PDF was produced again before anyone confirmed this one. */
        SUPERSEDED,
        TEST
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Kind kind;

    @Column(name = "cheque_id", updatable = false)
    private Long chequeId;

    @Column(name = "cheque_number", length = 30, updatable = false)
    private String chequeNumber;

    @Column(name = "layout_id", nullable = false, updatable = false)
    private Long layoutId;

    @Column(name = "printer_id", updatable = false)
    private Long printerId;

    @Column(name = "printer_name", length = 80, updatable = false)
    private String printerName;

    @Column(name = "batch_ref", nullable = false, length = 36, updatable = false)
    private String batchRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Outcome outcome;

    @Column(length = 300)
    private String note;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    public static PrintLog forCheque(Long companyId, Long branchId, Kind kind, Long chequeId, String chequeNumber,
                                     ChequeLayout layout, PrinterProfile printer, String batchRef) {
        PrintLog log = base(companyId, branchId, kind, layout, printer, batchRef);
        log.chequeId = chequeId;
        log.chequeNumber = chequeNumber;
        log.outcome = Outcome.PENDING;
        return log;
    }

    public static PrintLog test(ChequeLayout layout, PrinterProfile printer, String batchRef) {
        PrintLog log = base(layout.getCompanyId(), layout.getBranchId(), Kind.TEST, layout, printer, batchRef);
        log.outcome = Outcome.TEST;
        return log;
    }

    private static PrintLog base(Long companyId, Long branchId, Kind kind, ChequeLayout layout, PrinterProfile printer,
                                 String batchRef) {
        PrintLog log = new PrintLog();
        log.assignScope(companyId, branchId);
        log.kind = kind;
        log.layoutId = layout.getId();
        log.printerId = printer == null ? null : printer.getId();
        log.printerName = printer == null ? null : printer.getName();
        log.batchRef = batchRef;
        return log;
    }

    public void decide(Outcome outcome, String note, String user, Instant at) {
        if (this.outcome != Outcome.PENDING) {
            throw new BusinessException("This print of leaf " + chequeNumber + " was already marked "
                    + this.outcome.name().toLowerCase() + ".");
        }
        this.outcome = outcome;
        this.note = Text.clean(note);
        this.decidedBy = user;
        this.decidedAt = at;
    }

    public boolean isPending() {
        return outcome == Outcome.PENDING;
    }
}
