package com.chequetrack.issued.web;

import com.chequetrack.attachment.domain.AttachmentCategory;
import com.chequetrack.attachment.service.AttachmentService;
import com.chequetrack.audit.service.AuditQueryService;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.issued.IssuedPermissions;
import com.chequetrack.issued.domain.Crossing;
import com.chequetrack.issued.domain.IssuedCheque;
import com.chequetrack.issued.dto.IssuedChequeForm;
import com.chequetrack.issued.service.IssuedChequeLifecycle;
import com.chequetrack.issued.service.IssuedChequeService;
import com.chequetrack.lifecycle.domain.ChequeLifecycles;
import com.chequetrack.lifecycle.domain.IssuedStatus;
import com.chequetrack.lifecycle.domain.Transition;
import com.chequetrack.lifecycle.service.ApprovalService;
import com.chequetrack.lifecycle.service.TransitionInput;
import com.chequetrack.lifecycle.service.TransitionOutcome;
import com.chequetrack.lifecycle.service.TransitionService;
import com.chequetrack.security.service.AppUserPrincipal;
import com.chequetrack.security.service.CurrentUser;
import com.chequetrack.setup.domain.CompanySettings;
import com.chequetrack.setup.domain.PartyType;
import com.chequetrack.setup.domain.Reason;
import com.chequetrack.setup.domain.ReasonType;
import com.chequetrack.setup.service.CompanyAccountService;
import com.chequetrack.setup.service.PartyService;
import com.chequetrack.setup.service.ReasonService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/issued")
public class IssuedChequeController {

    private static final String MANAGE = "hasAuthority('" + IssuedPermissions.ISSUED_MANAGE + "')";

    /** Reason list offered for each action (others take a free-text reason). */
    private static final Map<String, ReasonType> REASON_LISTS = Map.of(
            "VOID", ReasonType.VOID, "STOP_PAYMENT", ReasonType.STOP_PAYMENT, "DEFER", ReasonType.DEFER,
            "CANCEL", ReasonType.CANCEL);

    /** Status tabs of the register, in display order. */
    private static final Map<String, Set<IssuedStatus>> TABS = new LinkedHashMap<>();

    static {
        TABS.put("draft", EnumSet.of(IssuedStatus.DRAFT, IssuedStatus.PENDING_APPROVAL));
        TABS.put("approved", EnumSet.of(IssuedStatus.APPROVED));
        TABS.put("printed", EnumSet.of(IssuedStatus.PRINTED));
        TABS.put("issued", EnumSet.of(IssuedStatus.ISSUED));
        TABS.put("cleared", EnumSet.of(IssuedStatus.CLEARED));
        TABS.put("closed", EnumSet.of(IssuedStatus.VOID, IssuedStatus.STOPPED, IssuedStatus.CANCELLED));
        TABS.put("all", EnumSet.allOf(IssuedStatus.class));
    }

    /** Printing on the bank layout, including reprints, comes in M10. */
    private static final Set<String> HIDDEN_ACTIONS = Set.of("REPRINT");

    private final IssuedChequeService chequeService;
    private final TransitionService transitionService;
    private final ApprovalService approvalService;
    private final PartyService partyService;
    private final CompanyAccountService accountService;
    private final ReasonService reasonService;
    private final AuditQueryService auditQueryService;
    private final AttachmentService attachmentService;
    private final Clock clock;

    public IssuedChequeController(IssuedChequeService chequeService,
                                  TransitionService transitionService,
                                  ApprovalService approvalService,
                                  PartyService partyService,
                                  CompanyAccountService accountService,
                                  ReasonService reasonService,
                                  AuditQueryService auditQueryService,
                                  AttachmentService attachmentService,
                                  Clock clock) {
        this.chequeService = chequeService;
        this.transitionService = transitionService;
        this.approvalService = approvalService;
        this.partyService = partyService;
        this.accountService = accountService;
        this.reasonService = reasonService;
        this.auditQueryService = auditQueryService;
        this.attachmentService = attachmentService;
        this.clock = clock;
    }

    // ---- register ------------------------------------------------------------------------------

    @GetMapping
    public String list(@RequestParam(defaultValue = "draft") String tab,
                       @RequestParam(required = false) Long partyId,
                       @RequestParam(required = false) Long accountId,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        if (!TABS.containsKey(tab)) {
            tab = "draft";
        }
        Page<IssuedCheque> cheques = chequeService.search(TABS.get(tab), partyId, accountId, from, to, q, page);
        model.addAttribute("cheques", cheques);
        model.addAttribute("tabs", TABS.keySet());
        model.addAttribute("tab", tab);
        model.addAttribute("partyId", partyId);
        model.addAttribute("accountId", accountId);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("q", q);
        model.addAttribute("suppliers", partyService.selectable(PartyType.SUPPLIER));
        model.addAttribute("accounts", accountService.selectable());
        return "issued/list";
    }

    /** Supplier PDC register: cheques with suppliers, not yet paid by the bank. */
    @GetMapping("/pdc")
    public String pdc(@RequestParam(required = false) Long partyId, Model model) {
        List<IssuedCheque> cheques = chequeService.outstanding(partyId);
        CompanySettings settings = chequeService.settings();
        Map<Long, IssuedChequeService.Expiry> expiry = new HashMap<>();
        cheques.forEach(c -> expiry.put(c.getId(), chequeService.expiry(c, settings)));
        LocalDate today = LocalDate.now(clock);
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal dueNow = BigDecimal.ZERO;
        BigDecimal postDated = BigDecimal.ZERO;
        for (IssuedCheque c : cheques) {
            total = total.add(c.getAmount());
            if (c.getChequeDate().isAfter(today)) {
                postDated = postDated.add(c.getAmount());
            } else {
                dueNow = dueNow.add(c.getAmount());
            }
        }
        model.addAttribute("cheques", cheques);
        model.addAttribute("expiry", expiry);
        model.addAttribute("total", total);
        model.addAttribute("dueNow", dueNow);
        model.addAttribute("postDated", postDated);
        model.addAttribute("partyId", partyId);
        model.addAttribute("suppliers", partyService.selectable(PartyType.SUPPLIER));
        return "issued/pdc";
    }

    // ---- writing -------------------------------------------------------------------------------

    @GetMapping("/new")
    @PreAuthorize(MANAGE)
    public String newForm(@RequestParam(required = false) Long accountId,
                          @RequestParam(required = false) Long partyId,
                          @RequestParam(required = false) Long reissueOf,
                          Model model, RedirectAttributes redirect) {
        IssuedChequeForm form;
        if (reissueOf != null) {
            try {
                form = chequeService.reissueForm(reissueOf);
            } catch (BusinessException ex) {
                redirect.addFlashAttribute("errorMessage", ex.getMessage());
                return "redirect:/issued/" + reissueOf;
            }
            if (accountId != null) {
                form.setAccountId(accountId);
            }
            if (partyId != null && !partyId.equals(form.getPartyId())) {
                form.setPartyId(partyId);
                form.setPayeeName(null);
                form.setAllocations(chequeService.allocationLines(partyId, null));
            }
        } else {
            form = new IssuedChequeForm();
            form.setAccountId(accountId);
            form.setPartyId(partyId);
            form.setChequeDate(LocalDate.now(clock));
            form.setAllocations(chequeService.allocationLines(partyId, null));
        }
        model.addAttribute("form", form);
        return showForm(model, form);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") IssuedChequeForm form, BindingResult binding, Model model,
                         RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                IssuedCheque c = chequeService.create(form);
                redirect.addFlashAttribute("successMessage", "Draft cheque saved on leaf " + c.getChequeNumber()
                        + ". Check it and submit it for approval.");
                return "redirect:/issued/" + c.getId();
            } catch (BusinessException ex) {
                binding.reject("cheque", ex.getMessage());
            }
        }
        return showForm(model, form);
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize(MANAGE)
    public String editForm(@PathVariable Long id, @RequestParam(required = false) Long partyId, Model model,
                           RedirectAttributes redirect) {
        IssuedChequeForm form;
        try {
            form = chequeService.editForm(id);
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/issued/" + id;
        }
        if (partyId != null && !partyId.equals(form.getPartyId())) {
            form.setPartyId(partyId);
            form.setPayeeName(null);
            form.setAllocations(chequeService.allocationLines(partyId, null));
        }
        model.addAttribute("form", form);
        return showForm(model, form);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") IssuedChequeForm form,
                         BindingResult binding, Model model, RedirectAttributes redirect) {
        form.setId(id);
        if (!binding.hasErrors()) {
            try {
                chequeService.update(id, form);
                redirect.addFlashAttribute("successMessage", "Draft cheque updated.");
                return "redirect:/issued/" + id;
            } catch (BusinessException ex) {
                binding.reject("cheque", ex.getMessage());
            }
        }
        return showForm(model, form);
    }

    // ---- one cheque ----------------------------------------------------------------------------

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        IssuedCheque c = chequeService.get(id);
        AppUserPrincipal me = CurrentUser.get();
        List<Transition> actions = transitionService.availableActions(IssuedCheque.RECORD_TYPE, c.lifecycleStatus()).stream()
                .filter(t -> !HIDDEN_ACTIONS.contains(t.action()))
                .filter(t -> !"STOP_PAYMENT".equals(t.action()) || me.hasPermission(IssuedPermissions.ISSUED_STOP_PAYMENT))
                .toList();
        Map<String, List<Reason>> reasons = new HashMap<>();
        REASON_LISTS.forEach((action, type) -> reasons.put(action, reasonService.active(type)));
        model.addAttribute("c", c);
        model.addAttribute("expiry", chequeService.expiry(c, chequeService.settings()));
        model.addAttribute("actions", actions);
        model.addAttribute("reasons", reasons);
        model.addAttribute("machine", ChequeLifecycles.ISSUED);
        model.addAttribute("today", LocalDate.now(clock));
        model.addAttribute("reissue", c.isReissuable() ? chequeService.reissueOf(id) : null);
        model.addAttribute("approvals", approvalService.forRecord(IssuedCheque.RECORD_TYPE, id));
        model.addAttribute("statusHistory", auditQueryService.statusHistory(IssuedCheque.RECORD_TYPE, id));
        model.addAttribute("auditEntries", auditQueryService.recentForEntity(IssuedCheque.RECORD_TYPE, id, 50));
        model.addAttribute("attachments", attachmentService.list(IssuedCheque.RECORD_TYPE, id));
        model.addAttribute("categories", AttachmentCategory.values());
        model.addAttribute("canManage", me.hasPermission(IssuedPermissions.ISSUED_MANAGE));
        return "issued/view";
    }

    /** Any status change: the engine decides whether it is allowed and whether it needs approval. */
    @PostMapping("/{id}/action/{action}")
    public String action(@PathVariable Long id, @PathVariable String action,
                         @RequestParam(required = false) Long reasonId,
                         @RequestParam(required = false) String note,
                         @RequestParam(required = false) String date,
                         @RequestParam(required = false) String collectedBy,
                         @RequestParam(required = false) String handoverNote,
                         RedirectAttributes redirect) {
        try {
            String reason = reasonText(action, reasonId, note);
            Map<String, Object> payload = new HashMap<>();
            putIfPresent(payload, IssuedChequeLifecycle.PAYLOAD_DATE, date);
            putIfPresent(payload, IssuedChequeLifecycle.PAYLOAD_COLLECTED_BY, collectedBy);
            putIfPresent(payload, IssuedChequeLifecycle.PAYLOAD_NOTE, handoverNote);
            TransitionOutcome outcome = transitionService.request(IssuedCheque.RECORD_TYPE, id, action,
                    TransitionInput.of(reason, payload));
            redirect.addFlashAttribute("successMessage", outcome.message());
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/issued/" + id;
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static void putIfPresent(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.isBlank()) {
            payload.put(key, value.trim());
        }
    }

    private String reasonText(String action, Long reasonId, String note) {
        String extra = note == null || note.isBlank() ? null : note.trim();
        if (reasonId == null) {
            return extra;
        }
        Reason reason = reasonService.get(reasonId);
        ReasonType expected = REASON_LISTS.get(action);
        if (expected == null || reason.getReasonType() != expected || !reason.isActive()) {
            throw new BusinessException("Choose a valid reason.");
        }
        return extra == null ? reason.getDescription() : reason.getDescription() + " – " + extra;
    }

    private String showForm(Model model, IssuedChequeForm form) {
        model.addAttribute("suppliers", partyService.selectable(PartyType.SUPPLIER));
        model.addAttribute("accounts", accountService.selectable());
        model.addAttribute("crossings", Crossing.values());
        if (form.getId() == null) {
            model.addAttribute("nextLeaf", chequeService.nextLeafNumber(form.getAccountId()));
        } else {
            model.addAttribute("draft", chequeService.get(form.getId()));
        }
        return "issued/form";
    }
}
