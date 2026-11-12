package com.chequetrack.report.service;

import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.exception.NotFoundException;
import com.chequetrack.common.web.DisplayFormat;
import com.chequetrack.report.definition.ReportDefinition;
import com.chequetrack.report.definition.ReportSupport;
import com.chequetrack.report.model.ReportFilter;
import com.chequetrack.report.model.ReportTable;
import com.chequetrack.security.service.AppUserPrincipal;
import com.chequetrack.security.service.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReportService {

    public enum Format { XLSX, PDF }

    /** Catalogue order. */
    private static final List<String> ORDER = List.of("received", "issued", "pdc-ageing", "bounced", "deposits", "stock",
            "void-stop", "history", "print-log");

    private final Map<String, ReportDefinition> reports;
    private final List<ReportDefinition> ordered;
    private final ReportSupport support;
    private final ExcelExporter excel;
    private final PdfExporter pdf;
    private final DisplayFormat format;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ReportService(List<ReportDefinition> definitions, ReportSupport support, ExcelExporter excel, PdfExporter pdf,
                         DisplayFormat format, ApplicationEventPublisher events, Clock clock) {
        this.ordered = definitions.stream()
                .sorted(java.util.Comparator.comparingInt(d -> ORDER.indexOf(d.code()) < 0 ? 99 : ORDER.indexOf(d.code())))
                .toList();
        this.reports = definitions.stream().collect(Collectors.toMap(ReportDefinition::code, Function.identity()));
        this.support = support;
        this.excel = excel;
        this.pdf = pdf;
        this.format = format;
        this.events = events;
        this.clock = clock;
    }

    /** Reports the current user may run, in catalogue order. */
    public List<ReportDefinition> available() {
        return ordered.stream().filter(this::allowed).toList();
    }

    public ReportDefinition definition(String code) {
        ReportDefinition def = reports.get(code);
        if (def == null) {
            throw new NotFoundException("Report", code);
        }
        if (!allowed(def)) {
            throw new AccessDeniedException("Missing permission for report " + code);
        }
        return def;
    }

    @Transactional(readOnly = true)
    public ReportTable run(String code, ReportFilter filter) {
        ReportDefinition def = definition(code);
        support.checkBranch(filter.getBranchId());
        return def.run(filter, support.describe(filter, def));
    }

    /** The file and its name; every export is written to the audit log. */
    @Transactional
    public Export export(String code, ReportFilter filter, Format fmt) {
        ReportTable table = run(code, filter);
        AppUserPrincipal me = CurrentUser.get();
        String footer = "ChequeTrack · " + table.title() + " · printed by " + me.getUsername() + " on " + format.ts(clock.instant());
        byte[] bytes = fmt == Format.PDF ? pdf.export(table, footer) : excel.export(table, footer);
        String name = code + "-" + java.time.LocalDate.now(clock) + (fmt == Format.PDF ? ".pdf" : ".xlsx");
        events.publishEvent(AuditEvent.of("REPORT_EXPORTED", "Report", code,
                "Exported '" + table.title() + "' as " + fmt.name() + " (" + table.size() + " rows): " + table.subtitle()));
        return new Export(bytes, name, fmt);
    }

    public record Export(byte[] bytes, String filename, Format format) {
    }

    private boolean allowed(ReportDefinition def) {
        AppUserPrincipal me = CurrentUser.get();
        return def.permissions().stream().anyMatch(me::hasPermission);
    }
}
