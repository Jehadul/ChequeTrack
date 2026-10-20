package com.chequetrack.lifecycle.web;

import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.lifecycle.LifecyclePermissions;
import com.chequetrack.lifecycle.domain.ApprovalRequest;
import com.chequetrack.lifecycle.service.ApprovalService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + LifecyclePermissions.APPROVAL_DECIDE + "')")
    public String inbox(Model model) {
        model.addAttribute("requests", approvalService.inbox());
        model.addAttribute("mode", "inbox");
        return "approval/list";
    }

    @GetMapping("/mine")
    public String mine(Model model) {
        model.addAttribute("requests", approvalService.mine());
        model.addAttribute("mode", "mine");
        return "approval/list";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        ApprovalRequest request = approvalService.get(id);
        model.addAttribute("r", request);
        model.addAttribute("canDecide", approvalService.canDecide(request));
        model.addAttribute("recordUrl", approvalService.recordUrl(request));
        return "approval/view";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id, @RequestParam(required = false) String comment, RedirectAttributes redirect) {
        try {
            ApprovalRequest r = approvalService.approve(id, comment);
            redirect.addFlashAttribute("successMessage", r.getStatus().name().equals("APPROVED")
                    ? "Approved. The change has been carried out." : "Approved. Waiting for the next step.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/approvals/" + id;
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam(required = false) String comment, RedirectAttributes redirect) {
        try {
            approvalService.reject(id, comment);
            redirect.addFlashAttribute("successMessage", "Rejected.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/approvals/" + id;
    }

    @PostMapping("/{id}/withdraw")
    public String withdraw(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            approvalService.withdraw(id);
            redirect.addFlashAttribute("successMessage", "Request withdrawn.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/approvals/" + id;
    }
}
