package com.chequetrack.organization.web;

import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.organization.OrganizationPermissions;
import com.chequetrack.organization.dto.UserForm;
import com.chequetrack.organization.service.BranchService;
import com.chequetrack.organization.service.RoleService;
import com.chequetrack.organization.service.UserManagementService;
import com.chequetrack.organization.service.UserManagementService.CreatedUser;
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

@Controller
@RequestMapping("/admin/users")
public class UserController {

    private static final String FORM = "admin/users/form";

    private final UserManagementService userService;
    private final RoleService roleService;
    private final BranchService branchService;

    public UserController(UserManagementService userService, RoleService roleService, BranchService branchService) {
        this.userService = userService;
        this.roleService = roleService;
        this.branchService = branchService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.listVisible());
        return "admin/users/list";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("user", userService.view(id));
        return "admin/users/view";
    }

    @PreAuthorize("hasAuthority('" + OrganizationPermissions.USER_MANAGE + "')")
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new UserForm());
        return showForm(model);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") UserForm form, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                CreatedUser created = userService.create(form);
                redirect.addFlashAttribute("temporaryPassword", created.temporaryPassword());
                redirect.addFlashAttribute("successMessage", "User " + created.username() + " created.");
                return "redirect:/admin/users/" + created.id();
            } catch (BusinessException ex) {
                binding.reject("user", ex.getMessage());
            }
        }
        return showForm(model);
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", userService.editForm(id));
        return showForm(model);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") UserForm form,
                         BindingResult binding, Model model, RedirectAttributes redirect) {
        form.setId(id);
        if (!binding.hasErrors()) {
            try {
                userService.update(id, form);
                redirect.addFlashAttribute("successMessage", "User saved.");
                return "redirect:/admin/users/" + id;
            } catch (BusinessException ex) {
                binding.reject("user", ex.getMessage());
            }
        }
        return showForm(model);
    }

    @PostMapping("/{id}/enable")
    public String enable(@PathVariable Long id, @RequestParam(required = false) String reason,
                         RedirectAttributes redirect) {
        return action(id, redirect, () -> userService.setEnabled(id, true, reason), "User enabled.");
    }

    @PostMapping("/{id}/disable")
    public String disable(@PathVariable Long id, @RequestParam(required = false) String reason,
                          RedirectAttributes redirect) {
        return action(id, redirect, () -> userService.setEnabled(id, false, reason),
                "User disabled. Any open session ends on the next click.");
    }

    @PostMapping("/{id}/unlock")
    public String unlock(@PathVariable Long id, RedirectAttributes redirect) {
        return action(id, redirect, () -> userService.unlock(id), "User unlocked.");
    }

    @PostMapping("/{id}/reset-password")
    public String resetPassword(@PathVariable Long id, RedirectAttributes redirect) {
        return action(id, redirect, () -> redirect.addFlashAttribute("temporaryPassword", userService.resetPassword(id)),
                "Password reset. Give the temporary password below to the user.");
    }

    private String action(Long id, RedirectAttributes redirect, Runnable work, String success) {
        try {
            work.run();
            redirect.addFlashAttribute("successMessage", success);
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }

    private String showForm(Model model) {
        model.addAttribute("roles", roleService.list());
        model.addAttribute("branches", branchService.listActiveInCompany());
        return FORM;
    }
}
