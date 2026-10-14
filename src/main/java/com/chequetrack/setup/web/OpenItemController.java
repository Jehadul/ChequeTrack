package com.chequetrack.setup.web;

import com.chequetrack.audit.service.AuditQueryService;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.organization.domain.Branch;
import com.chequetrack.organization.scope.BranchScope;
import com.chequetrack.organization.service.BranchService;
import com.chequetrack.security.service.CurrentUser;
import com.chequetrack.setup.SetupPermissions;
import com.chequetrack.setup.domain.OpenItem;
import com.chequetrack.setup.domain.OpenItemType;
import com.chequetrack.setup.domain.PartyType;
import com.chequetrack.setup.dto.OpenItemForm;
import com.chequetrack.setup.service.OpenItemService;
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

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Customer invoices (/setup/invoices) and supplier bills (/setup/bills) share
 * one controller; the URL decides the type.
 */
@Controller
@RequestMapping("/setup/{kind:invoices|bills}")
public class OpenItemController {

    private static final String FORM = "setup/items/form";

    private final OpenItemService itemService;
    private final PartyService partyService;
    private final BranchScope branchScope;
    private final BranchService branchService;
    private final AuditQueryService auditQueryService;
    private final Clock clock;

    public OpenItemController(OpenItemService itemService,
                              PartyService partyService,
                              BranchScope branchScope,
                              BranchService branchService,
                              AuditQueryService auditQueryService,
                              Clock clock) {
        this.itemService = itemService;
        this.partyService = partyService;
        this.branchScope = branchScope;
        this.branchService = branchService;
        this.auditQueryService = auditQueryService;
        this.clock = clock;
    }

    @ModelAttribute("kind")
    public String kind(@PathVariable String kind) {
        return kind;
    }

    @ModelAttribute("itemType")
    public OpenItemType itemType(@PathVariable String kind) {
        return "invoices".equals(kind) ? OpenItemType.INVOICE : OpenItemType.BILL;
    }

    @GetMapping
    public String list(@ModelAttribute("itemType") OpenItemType type,
                       @RequestParam(defaultValue = "true") boolean openOnly,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("items", itemService.search(type, openOnly, q, page));
        model.addAttribute("openOnly", openOnly);
        model.addAttribute("q", q);
        model.addAttribute("branchCodes", branchCodes());
        return "setup/items/list";
    }

    @GetMapping("/{id}")
    public String view(@ModelAttribute("itemType") OpenItemType type, @PathVariable Long id, Model model) {
        OpenItem item = itemService.get(id);
        if (item.getItemType() != type) {
            return "redirect:/setup/" + (item.getItemType() == OpenItemType.INVOICE ? "invoices/" : "bills/") + id;
        }
        model.addAttribute("item", item);
        model.addAttribute("branchCodes", branchCodes());
        model.addAttribute("statusHistory", auditQueryService.statusHistory(OpenItemService.ENTITY, id));
        model.addAttribute("auditEntries", auditQueryService.recentForEntity(OpenItemService.ENTITY, id, 50));
        return "setup/items/view";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('" + SetupPermissions.INVOICE_MANAGE + "')")
    public String newForm(@ModelAttribute("itemType") OpenItemType type, Model model) {
        OpenItemForm form = new OpenItemForm();
        form.setItemType(type);
        form.setItemDate(LocalDate.now(clock));
        form.setBranchId(CurrentUser.get().getHomeBranchId());
        model.addAttribute("form", form);
        return showForm(type, model);
    }

    @PostMapping
    public String create(@ModelAttribute("itemType") OpenItemType type, @PathVariable String kind,
                         @Valid @ModelAttribute("form") OpenItemForm form, BindingResult binding, Model model,
                         RedirectAttributes redirect) {
        form.setItemType(type);
        if (!binding.hasErrors()) {
            try {
                OpenItem item = itemService.create(form);
                redirect.addFlashAttribute("successMessage", type.getLabel() + " " + item.getNumber() + " created.");
                return "redirect:/setup/" + kind + "/" + item.getId();
            } catch (BusinessException ex) {
                binding.reject("item", ex.getMessage());
            }
        }
        return showForm(type, model);
    }

    @GetMapping("/{id}/edit")
    public String editForm(@ModelAttribute("itemType") OpenItemType type, @PathVariable Long id, Model model) {
        model.addAttribute("form", itemService.editForm(id));
        return showForm(type, model);
    }

    @PostMapping("/{id}")
    public String update(@ModelAttribute("itemType") OpenItemType type, @PathVariable String kind, @PathVariable Long id,
                         @Valid @ModelAttribute("form") OpenItemForm form, BindingResult binding, Model model,
                         RedirectAttributes redirect) {
        form.setId(id);
        form.setItemType(type);
        if (!binding.hasErrors()) {
            try {
                itemService.update(id, form);
                redirect.addFlashAttribute("successMessage", "Saved.");
                return "redirect:/setup/" + kind + "/" + id;
            } catch (BusinessException ex) {
                binding.reject("item", ex.getMessage());
            }
        }
        return showForm(type, model);
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable String kind, @PathVariable Long id,
                         @RequestParam(required = false) String reason, RedirectAttributes redirect) {
        try {
            itemService.cancel(id, reason);
            redirect.addFlashAttribute("successMessage", "Cancelled.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/setup/" + kind + "/" + id;
    }

    private String showForm(OpenItemType type, Model model) {
        model.addAttribute("parties", partyService.selectable(type == OpenItemType.INVOICE ? PartyType.CUSTOMER : PartyType.SUPPLIER));
        model.addAttribute("branches", branchScope.selectableBranches());
        return FORM;
    }

    private Map<Long, String> branchCodes() {
        return branchService.listAllInCompany().stream().collect(Collectors.toMap(Branch::getId, Branch::getCode));
    }
}
