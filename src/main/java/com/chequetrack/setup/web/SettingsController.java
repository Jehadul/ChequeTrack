package com.chequetrack.setup.web;

import com.chequetrack.setup.dto.SettingsForm;
import com.chequetrack.setup.service.SettingsService;
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
@RequestMapping("/setup/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public String edit(Model model) {
        model.addAttribute("form", settingsService.editForm());
        return "setup/settings";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("form") SettingsForm form, BindingResult binding, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return "setup/settings";
        }
        settingsService.update(form);
        redirect.addFlashAttribute("successMessage", "Settings saved.");
        return "redirect:/setup/settings";
    }
}
