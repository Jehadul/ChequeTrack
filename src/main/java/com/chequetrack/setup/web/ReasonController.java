package com.chequetrack.setup.web;

import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.setup.SetupPermissions;
import com.chequetrack.setup.domain.ReasonType;
import com.chequetrack.setup.dto.ReasonForm;
import com.chequetrack.setup.service.ReasonService;
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

/** All reason lists on one page, with an add form; edit on its own page. */
@Controller
@RequestMapping("/setup/reasons")
@PreAuthorize("hasAuthority('" + SetupPermissions.SETTINGS_MANAGE + "')")
public class ReasonController {

    private final ReasonService reasonService;

    public ReasonController(ReasonService reasonService) {
        this.reasonService = reasonService;
    }

    @GetMapping
    public String list(Model model) {
        ReasonForm form = new ReasonForm();
        form.setReasonType(ReasonType.BOUNCE);
        model.addAttribute("form", form);
        return showList(model);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ReasonForm form, BindingResult binding, Model model,
                         RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                reasonService.create(form);
                redirect.addFlashAttribute("successMessage", "Reason added.");
                return "redirect:/setup/reasons#" + form.getReasonType();
            } catch (BusinessException ex) {
                binding.rejectValue("code", "duplicate", ex.getMessage());
            }
        }
        return showList(model);
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", reasonService.editForm(id));
        return "setup/reason-form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") ReasonForm form, BindingResult binding,
                         RedirectAttributes redirect) {
        form.setId(id);
        if (binding.hasErrors()) {
            return "setup/reason-form";
        }
        reasonService.update(id, form);
        redirect.addFlashAttribute("successMessage", "Reason saved.");
        return "redirect:/setup/reasons#" + form.getReasonType();
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes redirect) {
        reasonService.setActive(id, true);
        redirect.addFlashAttribute("successMessage", "Reason activated.");
        return "redirect:/setup/reasons#" + reasonService.get(id).getReasonType();
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, RedirectAttributes redirect) {
        reasonService.setActive(id, false);
        redirect.addFlashAttribute("successMessage", "Reason deactivated. Past records keep it.");
        return "redirect:/setup/reasons#" + reasonService.get(id).getReasonType();
    }

    private String showList(Model model) {
        model.addAttribute("groups", reasonService.allGrouped());
        model.addAttribute("reasonTypes", ReasonType.values());
        return "setup/reasons";
    }
}
