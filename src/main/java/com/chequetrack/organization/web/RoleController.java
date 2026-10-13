package com.chequetrack.organization.web;

import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.organization.OrganizationPermissions;
import com.chequetrack.organization.domain.Role;
import com.chequetrack.organization.dto.RoleForm;
import com.chequetrack.organization.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/admin/roles")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + OrganizationPermissions.ROLE_MANAGE + "')")
public class RoleController {

    private static final String FORM = "admin/roles/form";

    private final RoleService roleService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("roles", roleService.list());
        model.addAttribute("userCounts", roleService.userCounts());
        return "admin/roles/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new RoleForm());
        return showForm(null, model);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") RoleForm form, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                roleService.create(form);
                redirect.addFlashAttribute("successMessage", "Role " + form.getName() + " created.");
                return "redirect:/admin/roles";
            } catch (BusinessException ex) {
                binding.rejectValue("code", "duplicate", ex.getMessage());
            }
        }
        return showForm(null, model);
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", roleService.editForm(id));
        return showForm(id, model);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") RoleForm form,
                         BindingResult binding, Model model, RedirectAttributes redirect) {
        form.setId(id);
        if (!binding.hasErrors()) {
            try {
                roleService.update(id, form);
                redirect.addFlashAttribute("successMessage",
                        "Role saved. Users with this role get the new permissions on their next click.");
                return "redirect:/admin/roles";
            } catch (BusinessException ex) {
                binding.reject("role", ex.getMessage());
            }
        }
        return showForm(id, model);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            roleService.delete(id);
            redirect.addFlashAttribute("successMessage", "Role deleted.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/roles";
    }

    private String showForm(Long id, Model model) {
        Role role = id == null ? null : roleService.get(id);
        model.addAttribute("role", role);
        model.addAttribute("permissionGroups", roleService.permissionGroups());
        model.addAttribute("adminRole", role != null && OrganizationPermissions.ADMIN_ROLE.equals(role.getCode()));
        return FORM;
    }
}
