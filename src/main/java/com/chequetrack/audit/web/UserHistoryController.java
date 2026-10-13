package com.chequetrack.audit.web;

import com.chequetrack.audit.service.AuditQueryService;
import com.chequetrack.organization.dto.UserView;
import com.chequetrack.organization.service.UserManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * History tab of a user. Lives in the audit module because the organization
 * module (M2) may not depend on it. Access is checked by loading the user
 * through UserManagementService first: same permission and branch rules,
 * and an invisible user answers 404.
 */
@Controller
@RequiredArgsConstructor
public class UserHistoryController {

    private static final int RECENT = 100;

    private final UserManagementService userService;
    private final AuditQueryService auditQueryService;

    @GetMapping("/admin/users/{id}/history")
    public String history(@PathVariable Long id, Model model) {
        UserView user = userService.view(id);
        model.addAttribute("user", user);
        model.addAttribute("statusHistory", auditQueryService.statusHistory("User", id));
        model.addAttribute("auditEntries", auditQueryService.recentForEntity("User", id, RECENT));
        return "audit/user-history";
    }
}
