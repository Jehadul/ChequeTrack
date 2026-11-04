package com.chequetrack.printing.web;

import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.issued.IssuedPermissions;
import com.chequetrack.issued.domain.IssuedCheque;
import com.chequetrack.issued.service.IssuedChequeService;
import com.chequetrack.printing.domain.ChequeLayout;
import com.chequetrack.printing.domain.PrintLog;
import com.chequetrack.printing.service.ChequePdfRenderer;
import com.chequetrack.printing.service.PrintService;
import com.chequetrack.printing.service.PrinterService;
import com.chequetrack.setup.service.CompanyAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/printing")
public class PrintController {

    private static final String MANAGE = "hasAuthority('" + IssuedPermissions.ISSUED_MANAGE + "')";
    private static final String PRINTER_KEY = "printing.printerId";

    private final PrintService printService;
    private final PrinterService printerService;
    private final IssuedChequeService chequeService;
    private final CompanyAccountService accountService;
    private final ChequePdfRenderer renderer;

    public PrintController(PrintService printService,
                           PrinterService printerService,
                           IssuedChequeService chequeService,
                           CompanyAccountService accountService,
                           ChequePdfRenderer renderer) {
        this.printService = printService;
        this.printerService = printerService;
        this.chequeService = chequeService;
        this.accountService = accountService;
        this.renderer = renderer;
    }

    // ---- one cheque ----------------------------------------------------------------------------

    @GetMapping("/cheques/{id}")
    @PreAuthorize(MANAGE)
    public String cheque(@PathVariable Long id, HttpSession session, Model model) {
        IssuedCheque c = chequeService.get(id);
        ChequeLayout layout = printService.layoutFor(c);
        List<PrintLog> history = printService.history(id);
        model.addAttribute("c", c);
        model.addAttribute("layout", layout);
        model.addAttribute("texts", layout == null ? null : renderer.texts(layout, PrintService.dataOf(c)));
        model.addAttribute("history", history);
        model.addAttribute("pending", history.stream().filter(PrintLog::isPending).findFirst().orElse(null));
        model.addAttribute("printers", printerService.selectable());
        model.addAttribute("printerId", session.getAttribute(PRINTER_KEY));
        model.addAttribute("scale", 3);
        return "printing/cheque";
    }

    @PostMapping("/cheques/{id}/pdf")
    public ResponseEntity<byte[]> chequePdf(@PathVariable Long id, @RequestParam(required = false) Long printerId,
                                            HttpSession session, HttpServletRequest request, HttpServletResponse response) {
        return pdf(List.of(id), printerId, session, request, response, "/printing/cheques/" + id);
    }

    // ---- batch ---------------------------------------------------------------------------------

    @GetMapping("/batch")
    @PreAuthorize(MANAGE)
    public String batch(@RequestParam(required = false) Long accountId, HttpSession session, Model model) {
        model.addAttribute("accounts", accountService.selectable());
        model.addAttribute("accountId", accountId);
        if (accountId != null) {
            model.addAttribute("cheques", chequeService.approvedForPrinting(accountId));
            model.addAttribute("layoutName", layoutName(accountId));
        }
        model.addAttribute("printers", printerService.selectable());
        model.addAttribute("printerId", session.getAttribute(PRINTER_KEY));
        model.addAttribute("max", PrintService.MAX_BATCH);
        return "printing/batch";
    }

    @PostMapping("/batch/pdf")
    public ResponseEntity<byte[]> batchPdf(@RequestParam(name = "chequeIds", required = false) List<Long> chequeIds,
                                           @RequestParam(required = false) Long printerId,
                                           @RequestParam(required = false) Long accountId, HttpSession session,
                                           HttpServletRequest request, HttpServletResponse response) {
        return pdf(chequeIds, printerId, session, request, response, "/printing/batch" + (accountId == null ? "" : "?accountId=" + accountId));
    }

    // ---- confirming ----------------------------------------------------------------------------

    @GetMapping("/pending")
    @PreAuthorize(MANAGE)
    public String pending(Model model) {
        Map<String, List<PrintLog>> batches = new LinkedHashMap<>();
        printService.pending().forEach(p -> batches.computeIfAbsent(p.getBatchRef(), k -> new ArrayList<>()).add(p));
        model.addAttribute("batches", batches);
        return "printing/pending";
    }

    @PostMapping("/logs/{logId}/ok")
    public String ok(@PathVariable Long logId, @RequestParam(required = false) String back, RedirectAttributes redirect) {
        try {
            printService.confirm(logId);
            redirect.addFlashAttribute("successMessage", "Marked printed. The leaf is now used.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:" + safeBack(back);
    }

    @PostMapping("/logs/{logId}/spoiled")
    public String spoiled(@PathVariable Long logId, @RequestParam(required = false) String reason,
                          @RequestParam(required = false) String back, RedirectAttributes redirect) {
        try {
            IssuedCheque c = printService.spoiled(logId, reason);
            redirect.addFlashAttribute("successMessage", "The spoiled leaf is void. The cheque is now on leaf "
                    + c.getChequeNumber() + ": put that leaf in the printer and print again.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:" + safeBack(back);
    }

    @PostMapping("/batches/{batchRef}/ok")
    public String batchOk(@PathVariable String batchRef, RedirectAttributes redirect) {
        try {
            int n = printService.confirmBatch(batchRef);
            redirect.addFlashAttribute("successMessage", n + " cheque(s) marked printed.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/printing/pending";
    }

    // ---- log -----------------------------------------------------------------------------------

    @GetMapping("/log")
    @PreAuthorize("hasAuthority('" + IssuedPermissions.ISSUED_VIEW + "')")
    public String log(@RequestParam(required = false) PrintLog.Kind kind,
                      @RequestParam(required = false) PrintLog.Outcome outcome,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                      @RequestParam(required = false) String q,
                      @RequestParam(defaultValue = "0") int page,
                      Model model) {
        model.addAttribute("logs", printService.search(kind, outcome, from, to, q, page));
        model.addAttribute("kind", kind);
        model.addAttribute("outcome", outcome);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("q", q);
        model.addAttribute("kinds", PrintLog.Kind.values());
        model.addAttribute("outcomes", PrintLog.Outcome.values());
        return "printing/log";
    }

    // ---- helpers -------------------------------------------------------------------------------

    /** The PDF, or back to the page with the reason when it cannot be produced. */
    private ResponseEntity<byte[]> pdf(List<Long> ids, Long printerId, HttpSession session, HttpServletRequest request,
                                      HttpServletResponse response, String back) {
        try {
            PrintService.Pdf pdf = printService.printCheques(ids, printerId);
            session.setAttribute(PRINTER_KEY, printerId);
            return PdfResponses.inline(pdf.bytes(), pdf.filename());
        } catch (BusinessException ex) {
            FlashMap flash = RequestContextUtils.getOutputFlashMap(request);
            flash.put("errorMessage", ex.getMessage());
            RequestContextUtils.saveOutputFlashMap(back, request, response);
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(request.getContextPath() + back)).build();
        }
    }

    private String layoutName(Long accountId) {
        ChequeLayout l = printService.layoutForAccount(accountId);
        return l == null ? null : l.getName();
    }

    /** Only our own pages, never an outside address. */
    private static String safeBack(String back) {
        return back != null && back.startsWith("/") && !back.startsWith("//") ? back : "/printing/pending";
    }
}
