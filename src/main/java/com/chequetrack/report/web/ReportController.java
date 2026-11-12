package com.chequetrack.report.web;

import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.organization.scope.BranchScope;
import com.chequetrack.report.definition.ReportDefinition;
import com.chequetrack.report.model.ReportFilter;
import com.chequetrack.report.service.ReportService;
import com.chequetrack.setup.domain.PartyType;
import com.chequetrack.setup.service.BankService;
import com.chequetrack.setup.service.PartyService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping("/reports")
public class ReportController {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportService reportService;
    private final BranchScope branchScope;
    private final BankService bankService;
    private final PartyService partyService;

    public ReportController(ReportService reportService, BranchScope branchScope, BankService bankService,
                            PartyService partyService) {
        this.reportService = reportService;
        this.branchScope = branchScope;
        this.bankService = bankService;
        this.partyService = partyService;
    }

    @GetMapping
    public String catalogue(Model model) {
        model.addAttribute("reports", reportService.available());
        return "report/index";
    }

    /** The report on screen; {@code run} is set once the filters have been submitted. */
    @GetMapping("/{code}")
    public String view(@PathVariable String code, @ModelAttribute("filter") ReportFilter filter,
                       @org.springframework.web.bind.annotation.RequestParam(required = false) String run, Model model) {
        ReportDefinition def = reportService.definition(code);
        model.addAttribute("def", def);
        model.addAttribute("branches", branchScope.selectableBranches());
        model.addAttribute("banks", bankService.list());
        if (def.partyType() != null) {
            model.addAttribute("parties", partyService.selectable(def.partyType()));
        } else if (def.filters().contains(ReportFilter.Field.PARTY)) {
            model.addAttribute("parties", partyService.selectable(PartyType.BOTH));
        }
        if (run != null) {
            try {
                model.addAttribute("table", reportService.run(code, filter));
            } catch (BusinessException ex) {
                model.addAttribute("errorMessage", ex.getMessage());
            }
        }
        return "report/view";
    }

    @GetMapping("/{code}/export.xlsx")
    public ResponseEntity<byte[]> excel(@PathVariable String code, @ModelAttribute ReportFilter filter) {
        return file(reportService.export(code, filter, ReportService.Format.XLSX), XLSX, false);
    }

    @GetMapping("/{code}/export.pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String code, @ModelAttribute ReportFilter filter) {
        return file(reportService.export(code, filter, ReportService.Format.PDF), MediaType.APPLICATION_PDF, true);
    }

    private static ResponseEntity<byte[]> file(ReportService.Export e, MediaType type, boolean inline) {
        ContentDisposition cd = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(e.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(e.bytes());
    }
}
