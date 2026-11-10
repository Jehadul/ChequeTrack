package com.chequetrack.dashboard.web;

import com.chequetrack.dashboard.service.DashboardService;
import com.chequetrack.issued.IssuedPermissions;
import com.chequetrack.lifecycle.LifecyclePermissions;
import com.chequetrack.lifecycle.domain.ApprovalRequest;
import com.chequetrack.lifecycle.domain.ApprovalStatus;
import com.chequetrack.lifecycle.service.ApprovalService;
import com.chequetrack.received.ReceivedPermissions;
import com.chequetrack.security.service.AppUserPrincipal;
import com.chequetrack.security.service.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class DashboardController {

    private static final int WIDGET_ROWS = 6;

    private final DashboardService dashboardService;
    private final ApprovalService approvalService;

    public DashboardController(DashboardService dashboardService, ApprovalService approvalService) {
        this.dashboardService = dashboardService;
        this.approvalService = approvalService;
    }

    @GetMapping("/")
    public String home(Model model) {
        AppUserPrincipal me = CurrentUser.get();
        boolean cheques = me.hasPermission(ReceivedPermissions.RECEIVED_VIEW) || me.hasPermission(IssuedPermissions.ISSUED_VIEW);
        model.addAttribute("showCheques", cheques);
        if (cheques) {
            model.addAttribute("kpis", dashboardService.kpis());
            model.addAttribute("forecast", dashboardService.forecast());
            model.addAttribute("activity", dashboardService.recentActivity(10));
        }
        boolean decides = me.hasPermission(LifecyclePermissions.APPROVAL_DECIDE);
        model.addAttribute("decides", decides);
        List<ApprovalRequest> approvals = decides ? approvalService.inbox()
                : approvalService.mine().stream().filter(r -> r.getStatus() == ApprovalStatus.PENDING).toList();
        model.addAttribute("approvalCount", approvals.size());
        model.addAttribute("approvals", approvals.size() > WIDGET_ROWS ? approvals.subList(0, WIDGET_ROWS) : approvals);
        return "dashboard/home";
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasAnyAuthority('" + ReceivedPermissions.RECEIVED_VIEW + "','" + IssuedPermissions.ISSUED_VIEW + "')")
    public String analytics(Model model) {
        model.addAttribute("a", dashboardService.analytics());
        return "dashboard/analytics";
    }
}
