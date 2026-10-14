package com.chequetrack.setup.web;

import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.organization.scope.BranchScope;
import com.chequetrack.security.service.CurrentUser;
import com.chequetrack.setup.SetupPermissions;
import com.chequetrack.setup.domain.AccountType;
import com.chequetrack.setup.dto.AccountForm;
import com.chequetrack.setup.service.BankService;
import com.chequetrack.setup.service.CompanyAccountService;
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

/** Company bank accounts (branch-scoped). */
@Controller
@RequestMapping("/setup/accounts")
public class CompanyAccountController {

    private static final String FORM = "setup/accounts/form";

    private final CompanyAccountService accountService;
    private final BankService bankService;
    private final BranchScope branchScope;

    public CompanyAccountController(CompanyAccountService accountService,
                                    BankService bankService,
                                    BranchScope branchScope) {
        this.accountService = accountService;
        this.bankService = bankService;
        this.branchScope = branchScope;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("accounts", accountService.listVisible());
        model.addAttribute("branchCodes", accountService.branchCodes());
        return "setup/accounts/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('" + SetupPermissions.BANK_MANAGE + "')")
    public String newForm(Model model) {
        AccountForm form = new AccountForm();
        form.setBranchId(CurrentUser.get().getHomeBranchId());
        model.addAttribute("form", form);
        return showForm(model);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") AccountForm form, BindingResult binding, Model model,
                         RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                accountService.create(form);
                redirect.addFlashAttribute("successMessage", "Account " + form.getAccountNumber() + " created.");
                return "redirect:/setup/accounts";
            } catch (BusinessException ex) {
                binding.reject("account", ex.getMessage());
            }
        }
        return showForm(model);
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", accountService.editForm(id));
        return showForm(model);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") AccountForm form, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        form.setId(id);
        if (!binding.hasErrors()) {
            try {
                accountService.update(id, form);
                redirect.addFlashAttribute("successMessage", "Account saved.");
                return "redirect:/setup/accounts";
            } catch (BusinessException ex) {
                binding.reject("account", ex.getMessage());
            }
        }
        return showForm(model);
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes redirect) {
        accountService.setActive(id, true);
        redirect.addFlashAttribute("successMessage", "Account activated.");
        return "redirect:/setup/accounts";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, RedirectAttributes redirect) {
        accountService.setActive(id, false);
        redirect.addFlashAttribute("successMessage", "Account deactivated.");
        return "redirect:/setup/accounts";
    }

    private String showForm(Model model) {
        model.addAttribute("branches", branchScope.selectableBranches());
        model.addAttribute("bankBranches", bankService.selectableBranches());
        model.addAttribute("accountTypes", AccountType.values());
        return FORM;
    }
}
