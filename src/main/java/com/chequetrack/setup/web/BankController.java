package com.chequetrack.setup.web;

import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.setup.SetupPermissions;
import com.chequetrack.setup.domain.Bank;
import com.chequetrack.setup.dto.BankBranchForm;
import com.chequetrack.setup.dto.BankForm;
import com.chequetrack.setup.service.BankService;
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

/** Banks and their branches. */
@Controller
@RequestMapping("/setup/banks")
public class BankController {

    private static final String FORM = "setup/banks/form";
    private static final String VIEW = "setup/banks/view";
    private static final String BRANCH_FORM = "setup/banks/branch-form";

    private final BankService bankService;

    public BankController(BankService bankService) {
        this.bankService = bankService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("banks", bankService.list());
        model.addAttribute("branchCounts", bankService.branchCounts());
        return "setup/banks/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('" + SetupPermissions.BANK_MANAGE + "')")
    public String newForm(Model model) {
        model.addAttribute("form", new BankForm());
        return FORM;
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") BankForm form, BindingResult binding, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return FORM;
        }
        try {
            Bank bank = bankService.create(form);
            redirect.addFlashAttribute("successMessage", "Bank " + bank.getName() + " created. Add its branches below.");
            return "redirect:/setup/banks/" + bank.getId();
        } catch (BusinessException ex) {
            binding.rejectValue("code", "duplicate", ex.getMessage());
            return FORM;
        }
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        showBank(id, model);
        model.addAttribute("branchForm", new BankBranchForm());
        return VIEW;
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", bankService.editForm(id));
        return FORM;
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") BankForm form, BindingResult binding,
                         RedirectAttributes redirect) {
        form.setId(id);
        if (binding.hasErrors()) {
            return FORM;
        }
        try {
            bankService.update(id, form);
        } catch (BusinessException ex) {
            binding.reject("bank", ex.getMessage());
            return FORM;
        }
        redirect.addFlashAttribute("successMessage", "Bank saved.");
        return "redirect:/setup/banks/" + id;
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes redirect) {
        bankService.setBankActive(id, true);
        redirect.addFlashAttribute("successMessage", "Bank activated.");
        return "redirect:/setup/banks/" + id;
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, RedirectAttributes redirect) {
        bankService.setBankActive(id, false);
        redirect.addFlashAttribute("successMessage", "Bank deactivated. It no longer appears in drop-downs.");
        return "redirect:/setup/banks/" + id;
    }

    // ---- branches ------------------------------------------------------------------------------

    @PostMapping("/{id}/branches")
    public String addBranch(@PathVariable Long id, @Valid @ModelAttribute("branchForm") BankBranchForm form,
                            BindingResult binding, Model model, RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                bankService.addBranch(id, form);
                redirect.addFlashAttribute("successMessage", "Branch " + form.getName() + " added.");
                return "redirect:/setup/banks/" + id;
            } catch (BusinessException ex) {
                binding.rejectValue("routingNumber", "duplicate", ex.getMessage());
            }
        }
        showBank(id, model);
        return VIEW;
    }

    @GetMapping("/branches/{branchId}/edit")
    public String editBranch(@PathVariable Long branchId, Model model) {
        model.addAttribute("form", bankService.branchEditForm(branchId));
        model.addAttribute("bank", bankService.getBranch(branchId).getBank());
        return BRANCH_FORM;
    }

    @PostMapping("/branches/{branchId}")
    public String updateBranch(@PathVariable Long branchId, @Valid @ModelAttribute("form") BankBranchForm form,
                               BindingResult binding, Model model, RedirectAttributes redirect) {
        form.setId(branchId);
        Long bankId = bankService.getBranch(branchId).getBank().getId();
        if (!binding.hasErrors()) {
            try {
                bankService.updateBranch(branchId, form);
                redirect.addFlashAttribute("successMessage", "Branch saved.");
                return "redirect:/setup/banks/" + bankId;
            } catch (BusinessException ex) {
                binding.rejectValue("routingNumber", "duplicate", ex.getMessage());
            }
        }
        model.addAttribute("bank", bankService.get(bankId));
        return BRANCH_FORM;
    }

    @PostMapping("/branches/{branchId}/activate")
    public String activateBranch(@PathVariable Long branchId, RedirectAttributes redirect) {
        Long bankId = bankService.setBranchActive(branchId, true);
        redirect.addFlashAttribute("successMessage", "Branch activated.");
        return "redirect:/setup/banks/" + bankId;
    }

    @PostMapping("/branches/{branchId}/deactivate")
    public String deactivateBranch(@PathVariable Long branchId, RedirectAttributes redirect) {
        Long bankId = bankService.getBranch(branchId).getBank().getId();
        try {
            bankService.setBranchActive(branchId, false);
            redirect.addFlashAttribute("successMessage", "Branch deactivated.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/setup/banks/" + bankId;
    }

    private void showBank(Long id, Model model) {
        model.addAttribute("bank", bankService.get(id));
        model.addAttribute("branches", bankService.branchesOf(id));
    }
}
