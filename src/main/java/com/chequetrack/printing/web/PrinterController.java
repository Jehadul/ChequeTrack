package com.chequetrack.printing.web;

import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.printing.PrintingPermissions;
import com.chequetrack.printing.domain.PrinterProfile;
import com.chequetrack.printing.dto.PrinterForm;
import com.chequetrack.printing.service.PrinterService;
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
@RequestMapping("/printing/printers")
@PreAuthorize("hasAuthority('" + PrintingPermissions.LAYOUT_MANAGE + "')")
public class PrinterController {

    private final PrinterService printerService;

    public PrinterController(PrinterService printerService) {
        this.printerService = printerService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long edit, Model model) {
        PrinterForm form = new PrinterForm();
        if (edit != null) {
            PrinterProfile p = printerService.list().stream().filter(x -> x.getId().equals(edit)).findFirst().orElse(null);
            if (p != null) {
                form.setId(p.getId());
                form.setVersion(p.getVersion());
                form.setName(p.getName());
                form.setOffsetXMm(p.getOffsetXMm());
                form.setOffsetYMm(p.getOffsetYMm());
                form.setNotes(p.getNotes());
            }
        }
        model.addAttribute("form", form);
        model.addAttribute("printers", printerService.list());
        return "printing/printers";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("form") PrinterForm form, BindingResult binding, Model model,
                       RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                PrinterProfile p = printerService.save(form);
                redirect.addFlashAttribute("successMessage", "Printer " + p.getName() + " saved.");
                return "redirect:/printing/printers";
            } catch (BusinessException ex) {
                binding.reject("printer", ex.getMessage());
            }
        }
        model.addAttribute("printers", printerService.list());
        return "printing/printers";
    }

    @PostMapping("/{id}/{state:activate|deactivate}")
    public String setActive(@PathVariable Long id, @PathVariable String state, RedirectAttributes redirect) {
        printerService.setActive(id, "activate".equals(state));
        redirect.addFlashAttribute("successMessage", "Printer " + ("activate".equals(state) ? "activated." : "deactivated."));
        return "redirect:/printing/printers";
    }
}
