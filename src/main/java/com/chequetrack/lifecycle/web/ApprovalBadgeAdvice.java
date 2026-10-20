package com.chequetrack.lifecycle.web;

import com.chequetrack.lifecycle.service.ApprovalService;
import com.chequetrack.security.service.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Adds the number of approvals waiting for the user's role to every page (sidebar badge). */
@ControllerAdvice(annotations = Controller.class)
public class ApprovalBadgeAdvice {

    private final ApprovalService approvalService;

    public ApprovalBadgeAdvice(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @ModelAttribute("pendingApprovalCount")
    public long pendingApprovalCount(HttpServletRequest request) {
        if (request.getRequestURI().startsWith(request.getContextPath() + "/api/") || CurrentUser.find().isEmpty()) {
            return 0;
        }
        return approvalService.inboxCount();
    }
}
