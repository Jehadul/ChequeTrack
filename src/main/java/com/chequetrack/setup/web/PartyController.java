package com.chequetrack.setup.web;

import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.setup.SetupPermissions;
import com.chequetrack.setup.domain.PartyType;
import com.chequetrack.setup.dto.PartyForm;
import com.chequetrack.setup.service.PartyService;
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

/** Customers and suppliers. */
@Controller
@RequestMapping("/setup/parties")
public class PartyController {

    private static final String FORM = "setup/parties/form";

    private final PartyService partyService;

    public PartyController(PartyService partyService) {
        this.partyService = partyService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) PartyType role,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("parties", partyService.search(role, q, page));
        model.addAttribute("role", role);
        model.addAttribute("q", q);
        return "setup/parties/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('" + SetupPermissions.PARTY_MANAGE + "')")
    public String newForm(@RequestParam(required = false) PartyType type, Model model) {
        PartyForm form = new PartyForm();
        if (type != null) {
            form.setPartyType(type);
        }
        model.addAttribute("form", form);
        return showForm(model);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") PartyForm form, BindingResult binding, Model model,
                         RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                partyService.create(form);
                redirect.addFlashAttribute("successMessage", form.getName() + " created.");
                return "redirect:/setup/parties";
            } catch (BusinessException ex) {
                binding.rejectValue("code", "duplicate", ex.getMessage());
            }
        }
        return showForm(model);
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", partyService.editForm(id));
        model.addAttribute("party", partyService.get(id));
        return showForm(model);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") PartyForm form, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        form.setId(id);
        if (!binding.hasErrors()) {
            try {
                partyService.update(id, form);
                redirect.addFlashAttribute("successMessage", form.getName() + " saved.");
                return "redirect:/setup/parties";
            } catch (BusinessException ex) {
                binding.reject("party", ex.getMessage());
            }
        }
        model.addAttribute("party", partyService.get(id));
        return showForm(model);
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes redirect) {
        partyService.setActive(id, true);
        redirect.addFlashAttribute("successMessage", "Activated.");
        return "redirect:/setup/parties/" + id + "/edit";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, RedirectAttributes redirect) {
        partyService.setActive(id, false);
        redirect.addFlashAttribute("successMessage", "Deactivated. It no longer appears in drop-downs.");
        return "redirect:/setup/parties/" + id + "/edit";
    }

    private String showForm(Model model) {
        model.addAttribute("partyTypes", PartyType.values());
        return FORM;
    }
}
