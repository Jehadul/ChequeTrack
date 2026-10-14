package com.chequetrack.organization.web;

import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.organization.dto.CompanyForm;
import com.chequetrack.organization.service.CompanyService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/company")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    public String edit(Model model) {
        model.addAttribute("form", companyService.editForm());
        model.addAttribute("company", companyService.current());
        return "admin/company";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("form") CompanyForm form, BindingResult binding,
                       Model model, RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                companyService.update(form);
                redirect.addFlashAttribute("successMessage", "Company profile saved.");
                return "redirect:/admin/company";
            } catch (BusinessException ex) {
                binding.reject("company", ex.getMessage());
            }
        }
        model.addAttribute("company", companyService.current());
        return "admin/company";
    }
}
