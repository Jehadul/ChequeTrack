package com.chequetrack.printing.service;

import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.issued.IssuedPermissions;
import com.chequetrack.issued.domain.Crossing;
import com.chequetrack.issued.domain.IssuedCheque;
import com.chequetrack.issued.service.IssuedChequeService;
import com.chequetrack.lifecycle.domain.IssuedStatus;
import com.chequetrack.lifecycle.service.TransitionInput;
import com.chequetrack.lifecycle.service.TransitionService;
import com.chequetrack.chequebook.domain.LeafStatus;
import com.chequetrack.organization.scope.BranchScope;
import com.chequetrack.printing.PrintingPermissions;
import com.chequetrack.printing.domain.ChequeLayout;
import com.chequetrack.printing.domain.PrintLog;
import com.chequetrack.printing.domain.PrinterProfile;
import com.chequetrack.printing.repository.PrintLogRepository;
import com.chequetrack.printing.service.ChequePdfRenderer.ChequeData;
import com.chequetrack.security.service.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class PrintService {

    public static final int PAGE_SIZE = 50;
    public static final int MAX_BATCH = 50;
    private static final String PRINT = "hasAuthority('" + IssuedPermissions.ISSUED_MANAGE + "')";
    private static final String VIEW = "hasAuthority('" + IssuedPermissions.ISSUED_VIEW + "')";
    private static final String LAYOUTS = "hasAuthority('" + PrintingPermissions.LAYOUT_MANAGE + "')";

    private final IssuedChequeService chequeService;
    private final TransitionService transitionService;
    private final LayoutService layoutService;
    private final PrinterService printerService;
    private final ChequePdfRenderer renderer;
    private final PrintLogRepository logRepository;
    private final BranchScope branchScope;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PrintService(IssuedChequeService chequeService,
                        TransitionService transitionService,
                        LayoutService layoutService,
                        PrinterService printerService,
                        ChequePdfRenderer renderer,
                        PrintLogRepository logRepository,
                        BranchScope branchScope,
                        ApplicationEventPublisher events,
                        Clock clock) {
        this.chequeService = chequeService;
        this.transitionService = transitionService;
        this.layoutService = layoutService;
        this.printerService = printerService;
        this.renderer = renderer;
        this.logRepository = logRepository;
        this.branchScope = branchScope;
        this.events = events;
        this.clock = clock;
    }

    /** A produced PDF. */
    public record Pdf(byte[] bytes, String filename, String batchRef) {
    }

    // ---- printing cheques ----------------------------------------------------------------------

    /**
     * Produces the PDF for approved cheques of one account, one page per cheque in leaf order,
     * and logs each as waiting for confirmation. Earlier unconfirmed prints of the same leaf are
     * marked superseded.
     */
    @Transactional
    @PreAuthorize(PRINT)
    public Pdf printCheques(Collection<Long> chequeIds, Long printerId) {
        if (chequeIds == null || chequeIds.isEmpty()) {
            throw new BusinessException("Choose the cheques to print.");
        }
        if (chequeIds.size() > MAX_BATCH) {
            throw new BusinessException("Print at most " + MAX_BATCH + " cheques at a time.");
        }
        List<IssuedCheque> cheques = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(chequeIds)) {
            IssuedCheque c = chequeService.get(id);
            if (c.getStatus() != IssuedStatus.APPROVED) {
                throw new BusinessException("Cheque " + c.getChequeNumber() + " is " + c.getStatus().getLabel().toLowerCase()
                        + ". Only approved cheques are printed.");
            }
            if (c.getLeaf().getStatus() != LeafStatus.ALLOCATED) {
                throw new BusinessException("Leaf " + c.getChequeNumber() + " is " + c.getLeaf().getStatus().name().toLowerCase() + ".");
            }
            cheques.add(c);
        }
        Long accountId = cheques.get(0).getAccount().getId();
        if (cheques.stream().anyMatch(c -> !c.getAccount().getId().equals(accountId))) {
            throw new BusinessException("Print cheques of one bank account at a time: each account has its own leaves and layout.");
        }
        ChequeLayout layout = requireLayout(accountId, cheques.get(0).getAccount().getLabel());
        PrinterProfile printer = printerService.find(printerId);
        cheques.sort(Comparator.comparing((IssuedCheque c) -> c.getLeaf().getPrefix())
                .thenComparingLong(c -> c.getLeaf().getLeafNumber()));

        String batchRef = UUID.randomUUID().toString();
        String user = CurrentUser.get().getUsername();
        Instant now = clock.instant();
        List<ChequeData> pages = new ArrayList<>();
        for (IssuedCheque c : cheques) {
            logRepository.findPendingForCheque(c.getId())
                    .forEach(old -> old.decide(PrintLog.Outcome.SUPERSEDED, "Printed again before confirming", user, now));
            PrintLog.Kind kind = logRepository.printedBefore(c.getId()) ? PrintLog.Kind.REPRINT : PrintLog.Kind.PRINT;
            logRepository.save(PrintLog.forCheque(c.getCompanyId(), c.getBranchId(), kind, c.getId(), c.getChequeNumber(),
                    layout, printer, batchRef));
            pages.add(dataOf(c));
            events.publishEvent(AuditEvent.of(kind == PrintLog.Kind.REPRINT ? "CHEQUE_REPRINT_PRODUCED" : "CHEQUE_PRINT_PRODUCED",
                    IssuedCheque.RECORD_TYPE, c.getId(),
                    (kind == PrintLog.Kind.REPRINT ? "Reprint" : "Print") + " of cheque " + c.getChequeNumber() + " to "
                            + c.getPayeeName() + ", Tk " + c.getAmount().toPlainString() + " with layout '" + layout.getName()
                            + "'" + (printer == null ? "" : " on " + printer.getLabel())));
        }
        byte[] pdf = renderer.render(layout, pages, printer, ChequePdfRenderer.Options.PRINT);
        String name = cheques.size() == 1 ? "cheque-" + cheques.get(0).getChequeNumber().replace(' ', '-')
                : "cheques-" + cheques.size() + "-" + batchRef.substring(0, 8);
        return new Pdf(pdf, name + ".pdf", batchRef);
    }

    /** The leaf printed correctly: the cheque becomes Printed (the guard closes the log entry). */
    @Transactional
    @PreAuthorize(PRINT)
    public void confirm(Long logId) {
        PrintLog log = pendingLog(logId);
        transitionService.request(IssuedCheque.RECORD_TYPE, log.getChequeId(), "PRINT", TransitionInput.of(null));
    }

    /** Every cheque of a batch printed correctly. */
    @Transactional
    @PreAuthorize(PRINT)
    public int confirmBatch(String batchRef) {
        List<PrintLog> pending = logRepository.findAllPending().stream()
                .filter(p -> p.getBatchRef().equals(batchRef)).toList();
        if (pending.isEmpty()) {
            throw new BusinessException("Nothing in this batch is waiting for confirmation.");
        }
        pending.forEach(p -> transitionService.request(IssuedCheque.RECORD_TYPE, p.getChequeId(), "PRINT", TransitionInput.of(null)));
        return pending.size();
    }

    /** The print came out wrong: the leaf is void and the cheque moves to the next leaf, to print again. */
    @Transactional
    @PreAuthorize(PRINT)
    public IssuedCheque spoiled(Long logId, String reason) {
        PrintLog log = pendingLog(logId);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Say what went wrong with the print.");
        }
        log.decide(PrintLog.Outcome.SPOILED, reason, CurrentUser.get().getUsername(), clock.instant());
        return chequeService.spoiledPrint(log.getChequeId(), reason);
    }

    // ---- layouts: preview and test print -------------------------------------------------------

    /** Sample cheque with the layout's own settings, for preview and test prints. */
    public ChequeData sample() {
        return new ChequeData(LocalDate.now(clock), "Karim Steel Limited", new BigDecimal("125000.50"), true);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(LAYOUTS)
    public byte[] preview(Long layoutId, Long printerId) {
        ChequeLayout layout = layoutService.get(layoutId);
        return renderer.render(layout, List.of(sample()), printerService.find(printerId), ChequePdfRenderer.Options.PRINT);
    }

    /** Sample data with an alignment grid, on plain paper laid over a real leaf. Logged. */
    @Transactional
    @PreAuthorize(LAYOUTS)
    public byte[] testPrint(Long layoutId, Long printerId) {
        ChequeLayout layout = layoutService.get(layoutId);
        PrinterProfile printer = printerService.find(printerId);
        logRepository.save(PrintLog.test(layout, printer, UUID.randomUUID().toString()));
        events.publishEvent(AuditEvent.of("LAYOUT_TEST_PRINT", ChequeLayout.RECORD_TYPE, layout.getId(),
                "Test print of layout '" + layout.getName() + "'" + (printer == null ? "" : " on " + printer.getLabel())));
        String footer = "TEST PRINT - " + layout.getName() + " - " + layout.getWidthMm() + " x " + layout.getHeightMm() + " mm"
                + (printer == null ? " - no printer offset" : " - " + printer.getName() + " offset "
                + printer.getOffsetXMm() + " / " + printer.getOffsetYMm() + " mm");
        return renderer.render(layout, List.of(sample()), printer, new ChequePdfRenderer.Options(true, footer));
    }

    // ---- reading -------------------------------------------------------------------------------

    /** The active layout for a cheque's account, or null. */
    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public ChequeLayout layoutFor(IssuedCheque cheque) {
        return layoutService.activeFor(cheque.getAccount().getId());
    }

    /** The active layout of an account, or null. */
    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public ChequeLayout layoutForAccount(Long accountId) {
        return layoutService.activeFor(accountId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public List<PrintLog> history(Long chequeId) {
        chequeService.get(chequeId);  // access check
        return logRepository.findByChequeIdOrderByIdDesc(chequeId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(PRINT)
    public List<PrintLog> pending() {
        return logRepository.findAllPending();
    }

    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public Page<PrintLog> search(PrintLog.Kind kind, PrintLog.Outcome outcome, LocalDate from, LocalDate to, String query,
                                 int page) {
        Collection<PrintLog.Kind> kinds = kind == null ? EnumSet.allOf(PrintLog.Kind.class) : EnumSet.of(kind);
        Collection<PrintLog.Outcome> outcomes = outcome == null ? EnumSet.allOf(PrintLog.Outcome.class) : EnumSet.of(outcome);
        Instant fromTs = from == null ? null : from.atStartOfDay(clock.getZone()).toInstant();
        Instant toTs = to == null ? null : to.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        String q = query == null || query.isBlank() ? "" : "%" + query.trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
        return logRepository.search(kinds, outcomes, fromTs, toTs, q,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Order.desc("id"))));
    }

    // ---- helpers -------------------------------------------------------------------------------

    private ChequeLayout requireLayout(Long accountId, String accountLabel) {
        ChequeLayout layout = layoutService.activeFor(accountId);
        if (layout == null) {
            throw new BusinessException("Account " + accountLabel + " has no active print layout. Ask an administrator "
                    + "to set one up under Printing → Cheque layouts.");
        }
        return layout;
    }

    private PrintLog pendingLog(Long logId) {
        PrintLog log = branchScope.require(logRepository.findById(logId), "Print", logId);
        if (!log.isPending() || log.getChequeId() == null) {
            throw new BusinessException("This print was already marked " + log.getOutcome().name().toLowerCase() + ".");
        }
        return log;
    }

    /** What is written on the cheque: for the PDF and the on-screen preview of the print page. */
    public static ChequeData dataOf(IssuedCheque c) {
        return new ChequeData(c.getChequeDate(), c.getPayeeName(), c.getAmount(), c.getCrossing() == Crossing.ACCOUNT_PAYEE);
    }
}
