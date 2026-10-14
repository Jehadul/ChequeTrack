package com.chequetrack.organization.web;

import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.organization.OrganizationPermissions;
import com.chequetrack.organization.dto.BranchForm;
import com.chequetrack.organization.service.BranchService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/branches")
public class BranchController {

    private static final String FORM = "admin/branches/form";

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("branches", branchService.listVisible());
        return "admin/branches/list";
    }

    @PreAuthorize("hasAuthority('" + OrganizationPermissions.BRANCH_MANAGE + "')")
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new BranchForm());
        return FORM;
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") BranchForm form, BindingResult binding,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return FORM;
        }
        try {
            branchService.create(form);
        } catch (BusinessException ex) {
            binding.rejectValue("code", "duplicate", ex.getMessage());
            return FORM;
        }
        redirect.addFlashAttribute("successMessage", "Branch " + form.getCode().toUpperCase() + " created.");
        return "redirect:/admin/branches";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", branchService.editForm(id));
        return FORM;
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") BranchForm form,
                         BindingResult binding, RedirectAttributes redirect) {
        form.setId(id);
        if (binding.hasErrors()) {
            return FORM;
        }
        try {
            branchService.update(id, form);
        } catch (BusinessException ex) {
            binding.reject("branch", ex.getMessage());
            return FORM;
        }
        redirect.addFlashAttribute("successMessage", "Branch saved.");
        return "redirect:/admin/branches";
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes redirect) {
        return setActive(id, true, redirect);
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, RedirectAttributes redirect) {
        return setActive(id, false, redirect);
    }

    private String setActive(Long id, boolean active, RedirectAttributes redirect) {
        try {
            branchService.setActive(id, active);
            redirect.addFlashAttribute("successMessage", active ? "Branch activated." : "Branch deactivated.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/branches";
    }
}
