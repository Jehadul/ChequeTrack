package com.chequetrack.setup.web;

import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.organization.service.RoleService;
import com.chequetrack.security.service.CurrentUser;
import com.chequetrack.setup.SetupPermissions;
import com.chequetrack.setup.domain.ApprovalAction;
import com.chequetrack.setup.dto.ApprovalRuleForm;
import com.chequetrack.setup.service.ApprovalRuleService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

/** Approval steps, with a preview of "who must approve this amount?". */
@Controller
@RequestMapping("/setup/approvals")
@PreAuthorize("hasAuthority('" + SetupPermissions.SETTINGS_MANAGE + "')")
public class ApprovalRuleController {

    private final ApprovalRuleService ruleService;
    private final RoleService roleService;

    public ApprovalRuleController(ApprovalRuleService ruleService, RoleService roleService) {
        this.ruleService = ruleService;
        this.roleService = roleService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) ApprovalAction previewAction,
                       @RequestParam(required = false) BigDecimal previewAmount,
                       Model model) {
        model.addAttribute("form", new ApprovalRuleForm());
        if (previewAction != null && previewAmount != null && previewAmount.signum() >= 0) {
            model.addAttribute("previewSteps",
                    ruleService.requiredSteps(CurrentUser.get().getCompanyId(), previewAction, previewAmount));
        }
        model.addAttribute("previewAction", previewAction);
        model.addAttribute("previewAmount", previewAmount);
        return showList(model);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ApprovalRuleForm form, BindingResult binding, Model model,
                         RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                ruleService.create(form);
                redirect.addFlashAttribute("successMessage", "Approval step added.");
                return "redirect:/setup/approvals";
            } catch (BusinessException ex) {
                binding.reject("rule", ex.getMessage());
            }
        }
        return showList(model);
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", ruleService.editForm(id));
        model.addAttribute("roles", roleService.list());
        return "setup/approval-form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") ApprovalRuleForm form,
                         BindingResult binding, Model model, RedirectAttributes redirect) {
        form.setId(id);
        if (!binding.hasErrors()) {
            try {
                ruleService.update(id, form);
                redirect.addFlashAttribute("successMessage", "Approval step saved.");
                return "redirect:/setup/approvals";
            } catch (BusinessException ex) {
                binding.reject("rule", ex.getMessage());
            }
        }
        model.addAttribute("roles", roleService.list());
        return "setup/approval-form";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        ruleService.delete(id);
        redirect.addFlashAttribute("successMessage", "Approval step removed.");
        return "redirect:/setup/approvals";
    }

    private String showList(Model model) {
        model.addAttribute("groups", ruleService.allGrouped());
        model.addAttribute("roleNames", ruleService.roleNames());
        model.addAttribute("roles", roleService.list());
        model.addAttribute("actions", ApprovalAction.values());
        return "setup/approvals";
    }
}
