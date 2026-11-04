package com.chequetrack.printing.web;

import com.chequetrack.common.domain.AmountInWords;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.printing.PrintingPermissions;
import com.chequetrack.printing.domain.ChequeLayout;
import com.chequetrack.printing.domain.DateStyle;
import com.chequetrack.printing.domain.FieldCode;
import com.chequetrack.printing.domain.FontFamily;
import com.chequetrack.printing.domain.LayoutField;
import com.chequetrack.printing.domain.TextAlign;
import com.chequetrack.printing.dto.FieldsForm;
import com.chequetrack.printing.dto.LayoutForm;
import com.chequetrack.printing.service.ChequePdfRenderer;
import com.chequetrack.printing.service.LayoutService;
import com.chequetrack.printing.service.PrintService;
import com.chequetrack.printing.service.PrinterService;
import com.chequetrack.setup.service.CompanyAccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/printing/layouts")
@PreAuthorize("hasAuthority('" + PrintingPermissions.LAYOUT_MANAGE + "')")
public class LayoutController {

    /** Designer scale: screen pixels per millimetre. */
    private static final int SCALE = 4;

    private final LayoutService layoutService;
    private final PrintService printService;
    private final PrinterService printerService;
    private final ChequePdfRenderer renderer;
    private final CompanyAccountService accountService;

    public LayoutController(LayoutService layoutService,
                            PrintService printService,
                            PrinterService printerService,
                            ChequePdfRenderer renderer,
                            CompanyAccountService accountService) {
        this.layoutService = layoutService;
        this.printService = printService;
        this.printerService = printerService;
        this.renderer = renderer;
        this.accountService = accountService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("layouts", layoutService.list());
        return "printing/layouts";
    }

    // ---- create and settings -------------------------------------------------------------------

    @GetMapping("/new")
    public String newForm(Model model) {
        LayoutForm form = new LayoutForm();
        form.setWidthMm(200.0);
        form.setHeightMm(90.0);
        model.addAttribute("form", form);
        return showForm(model);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") LayoutForm form, BindingResult binding, Model model,
                         RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                ChequeLayout l = layoutService.create(form);
                redirect.addFlashAttribute("successMessage", "Layout created with starting positions. Upload a scan of a blank "
                        + "leaf, then drag each field onto its place.");
                return "redirect:/printing/layouts/" + l.getId();
            } catch (BusinessException ex) {
                binding.reject("layout", ex.getMessage());
            }
        }
        return showForm(model);
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", layoutService.editForm(id));
        return showForm(model);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") LayoutForm form, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        form.setId(id);
        if (!binding.hasErrors()) {
            try {
                layoutService.update(id, form);
                redirect.addFlashAttribute("successMessage", "Layout settings saved.");
                return "redirect:/printing/layouts/" + id;
            } catch (BusinessException ex) {
                binding.reject("layout", ex.getMessage());
            }
        }
        return showForm(model);
    }

    // ---- designer ------------------------------------------------------------------------------

    @GetMapping("/{id}")
    public String designer(@PathVariable Long id, Model model) {
        ChequeLayout layout = layoutService.get(id);
        model.addAttribute("layout", layout);
        if (!model.containsAttribute("fieldsForm")) {
            model.addAttribute("fieldsForm", layoutService.fieldsForm(id));
        }
        return showDesigner(model, layout);
    }

    @PostMapping("/{id}/fields")
    public String saveFields(@PathVariable Long id, @Valid @ModelAttribute("fieldsForm") FieldsForm form, BindingResult binding,
                             Model model, RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                layoutService.saveFields(id, form);
                redirect.addFlashAttribute("successMessage", "Field settings saved.");
                return "redirect:/printing/layouts/" + id;
            } catch (BusinessException ex) {
                binding.reject("fields", ex.getMessage());
            }
        }
        ChequeLayout layout = layoutService.get(id);
        model.addAttribute("layout", layout);
        return showDesigner(model, layout);
    }

    /** A field dropped or resized in the designer; saved at once. */
    @PostMapping("/{id}/fields/{code}/move")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> move(@PathVariable Long id, @PathVariable FieldCode code,
                                                    @RequestParam double x, @RequestParam double y,
                                                    @RequestParam double width) {
        try {
            LayoutField f = layoutService.moveField(id, code, x, y, width);
            return ResponseEntity.ok(Map.of("x", f.getXMm(), "y", f.getYMm(), "width", f.getWidthMm()));
        } catch (BusinessException ex) {
            return ResponseEntity.unprocessableEntity().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/{id}/background")
    public String background(@PathVariable Long id, @RequestParam("file") MultipartFile file, RedirectAttributes redirect) {
        try {
            layoutService.setBackground(id, file);
            redirect.addFlashAttribute("successMessage", "Scan uploaded. It is shown behind the fields and never printed.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/printing/layouts/" + id;
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            layoutService.activate(id);
            redirect.addFlashAttribute("successMessage", "This layout is now used for the account's cheques.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/printing/layouts/" + id;
    }

    @PostMapping("/{id}/copy")
    public String copy(@PathVariable Long id, @RequestParam(required = false) Long targetAccountId,
                       @RequestParam(required = false) String name, RedirectAttributes redirect) {
        try {
            ChequeLayout copy = layoutService.copy(id, targetAccountId, name);
            redirect.addFlashAttribute("successMessage", "Copied. Adjust the copy, then make it active.");
            return "redirect:/printing/layouts/" + copy.getId();
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/printing/layouts/" + id;
        }
    }

    // ---- preview and test print ----------------------------------------------------------------

    @GetMapping("/{id}/preview.pdf")
    public ResponseEntity<byte[]> preview(@PathVariable Long id, @RequestParam(required = false) Long printerId) {
        return PdfResponses.inline(printService.preview(id, printerId), "layout-" + id + "-preview.pdf");
    }

    @PostMapping("/{id}/test-print")
    public ResponseEntity<byte[]> testPrint(@PathVariable Long id, @RequestParam(required = false) Long printerId) {
        return PdfResponses.inline(printService.testPrint(id, printerId), "layout-" + id + "-test.pdf");
    }

    // ---- helpers -------------------------------------------------------------------------------

    private String showForm(Model model) {
        model.addAttribute("accounts", accountService.selectable());
        model.addAttribute("languages", AmountInWords.Language.values());
        model.addAttribute("dateStyles", DateStyle.values());
        return "printing/layout-form";
    }

    private String showDesigner(Model model, ChequeLayout layout) {
        model.addAttribute("scale", SCALE);
        model.addAttribute("texts", renderer.texts(layout, printService.sample()));
        model.addAttribute("printers", printerService.selectable());
        model.addAttribute("accounts", accountService.selectable());
        model.addAttribute("fontFamilies", FontFamily.values());
        model.addAttribute("aligns", TextAlign.values());
        return "printing/designer";
    }
}
