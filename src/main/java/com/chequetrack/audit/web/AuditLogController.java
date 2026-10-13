package com.chequetrack.audit.web;

import com.chequetrack.audit.AuditPermissions;
import com.chequetrack.audit.domain.AuditLogEntry;
import com.chequetrack.audit.service.AuditQueryService;
import com.chequetrack.audit.service.AuditQueryService.AuditFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/** Read-only, filterable audit log for administrators and auditors. */
@Controller
@RequestMapping("/audit")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + AuditPermissions.AUDIT_VIEW + "')")
public class AuditLogController {

    private final AuditQueryService auditQueryService;

    @GetMapping
    public String log(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                      @RequestParam(required = false) String username,
                      @RequestParam(required = false) String action,
                      @RequestParam(required = false) String entityType,
                      @RequestParam(defaultValue = "0") int page,
                      Model model) {
        AuditFilter filter = new AuditFilter(from, to, username, action, entityType);
        Page<AuditLogEntry> entries = auditQueryService.search(filter, page);
        model.addAttribute("filter", filter);
        model.addAttribute("entries", entries);
        model.addAttribute("actions", auditQueryService.actions());
        model.addAttribute("entityTypes", auditQueryService.entityTypes());
        return "audit/log";
    }
}
