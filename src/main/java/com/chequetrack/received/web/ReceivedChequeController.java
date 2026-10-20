package com.chequetrack.received.web;

import com.chequetrack.attachment.domain.AttachmentCategory;
import com.chequetrack.attachment.service.AttachmentService;
import com.chequetrack.audit.service.AuditQueryService;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.lifecycle.domain.ChequeLifecycles;
import com.chequetrack.lifecycle.domain.ReceivedStatus;
import com.chequetrack.lifecycle.service.ApprovalService;
import com.chequetrack.lifecycle.service.TransitionInput;
import com.chequetrack.lifecycle.service.TransitionOutcome;
import com.chequetrack.lifecycle.service.TransitionService;
import com.chequetrack.organization.scope.BranchScope;
import com.chequetrack.received.ReceivedPermissions;
import com.chequetrack.received.domain.ChequeType;
import com.chequetrack.received.domain.ReceivedCheque;
import com.chequetrack.received.dto.BulkChequeForm;
import com.chequetrack.received.dto.ChequeForm;
import com.chequetrack.received.service.ReceivedChequeLifecycle;
import com.chequetrack.received.service.ReceivedChequeService;
import com.chequetrack.security.service.CurrentUser;
import com.chequetrack.setup.domain.CompanySettings;
import com.chequetrack.setup.domain.PartyType;
import com.chequetrack.setup.domain.Reason;
import com.chequetrack.setup.domain.ReasonType;
import com.chequetrack.setup.service.BankService;
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

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/received")
public class ReceivedChequeController {

    private static final String MANAGE = "hasAuthority('" + ReceivedPermissions.RECEIVED_MANAGE + "')";

    /** Reason list offered for each action (others take a free-text reason). */
    private static final Map<String, ReasonType> REASON_LISTS = Map.of(
            "BOUNCE", ReasonType.BOUNCE, "DEFER", ReasonType.DEFER, "RETURN", ReasonType.RETURN,
            "REPLACE", ReasonType.REPLACE, "CANCEL", ReasonType.CANCEL);

    /** Status tabs of the register. */
    private static final Map<String, Set<ReceivedStatus>> TABS = Map.of(
            "in-hand", EnumSet.of(ReceivedStatus.IN_HAND),
            "deposited", EnumSet.of(ReceivedStatus.DEPOSITED),
            "bounced", EnumSet.of(ReceivedStatus.BOUNCED),
            "cleared", EnumSet.of(ReceivedStatus.CLEARED),
            "closed", EnumSet.of(ReceivedStatus.RETURNED, ReceivedStatus.REPLACED, ReceivedStatus.WRITTEN_OFF, ReceivedStatus.CANCELLED),
            "all", EnumSet.allOf(ReceivedStatus.class));

    private final ReceivedChequeService chequeService;
    private final TransitionService transitionService;
    private final ApprovalService approvalService;
    private final PartyService partyService;
    private final BankService bankService;
    private final ReasonService reasonService;
    private final AuditQueryService auditQueryService;
    private final AttachmentService attachmentService;
    private final BranchScope branchScope;
    private final Clock clock;

    public ReceivedChequeController(ReceivedChequeService chequeService,
                                    TransitionService transitionService,
                                    ApprovalService approvalService,
                                    PartyService partyService,
                                    BankService bankService,
                                    ReasonService reasonService,
                                    AuditQueryService auditQueryService,
                                    AttachmentService attachmentService,
                                    BranchScope branchScope,
                                    Clock clock) {
        this.chequeService = chequeService;
        this.transitionService = transitionService;
        this.approvalService = approvalService;
        this.partyService = partyService;
        this.bankService = bankService;
        this.reasonService = reasonService;
        this.auditQueryService = auditQueryService;
        this.attachmentService = attachmentService;
        this.branchScope = branchScope;
        this.clock = clock;
    }

    // ---- register ------------------------------------------------------------------------------

    @GetMapping
    public String list(@RequestParam(defaultValue = "in-hand") String tab,
                       @RequestParam(required = false) Long partyId,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Set<ReceivedStatus> statuses = TABS.getOrDefault(tab, TABS.get("in-hand"));
        Page<ReceivedCheque> cheques = chequeService.search(statuses, partyId, from, to, q, page);
        CompanySettings settings = chequeService.settings();
        Map<Long, ReceivedChequeService.Expiry> expiry = new HashMap<>();
        cheques.forEach(c -> expiry.put(c.getId(), chequeService.expiry(c, settings)));
        model.addAttribute("cheques", cheques);
        model.addAttribute("expiry", expiry);
        model.addAttribute("tab", tab);
        model.addAttribute("partyId", partyId);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("q", q);
        model.addAttribute("customers", partyService.selectable(PartyType.CUSTOMER));
        return "received/list";
    }

    // ---- entry ---------------------------------------------------------------------------------

    @GetMapping("/new")
    @PreAuthorize(MANAGE)
    public String newForm(@RequestParam(required = false) Long partyId,
                          @RequestParam(required = false) Long replaces, Model model) {
        ChequeForm form = new ChequeForm();
        form.setPartyId(partyId);
        form.setReplacesId(replaces);
        form.setReceivedDate(LocalDate.now(clock));
        form.setChequeDate(LocalDate.now(clock));
        form.setBranchId(CurrentUser.get().getHomeBranchId());
        form.setAllocations(chequeService.allocationLines(partyId));
        model.addAttribute("form", form);
        return showForm(model);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ChequeForm form, BindingResult binding, Model model,
                         RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                ReceivedCheque c = chequeService.create(form);
                redirect.addFlashAttribute("successMessage", "Cheque " + c.getChequeNumber() + " recorded. You can attach its scan below.");
                return "redirect:/received/" + c.getId();
            } catch (BusinessException ex) {
                binding.reject("cheque", ex.getMessage());
            }
        }
        return showForm(model);
    }

    @GetMapping("/bulk")
    @PreAuthorize(MANAGE)
    public String bulkForm(Model model) {
        BulkChequeForm form = new BulkChequeForm();
        form.setReceivedDate(LocalDate.now(clock));
        form.setFirstDate(LocalDate.now(clock).plusMonths(1).withDayOfMonth(1));
        form.setBranchId(CurrentUser.get().getHomeBranchId());
        model.addAttribute("form", form);
        return showBulkForm(model);
    }

    @PostMapping("/bulk")
    public String bulkCreate(@Valid @ModelAttribute("form") BulkChequeForm form, BindingResult binding, Model model,
                             RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                List<ReceivedCheque> created = chequeService.createSeries(form);
                redirect.addFlashAttribute("successMessage", created.size() + " cheques recorded ("
                        + created.get(0).getChequeNumber() + " to " + created.get(created.size() - 1).getChequeNumber() + ").");
                return "redirect:/received?tab=in-hand&partyId=" + form.getPartyId();
            } catch (BusinessException ex) {
                binding.reject("cheque", ex.getMessage());
            }
        }
        return showBulkForm(model);
    }

    // ---- one cheque ----------------------------------------------------------------------------

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        ReceivedCheque c = chequeService.get(id);
        model.addAttribute("c", c);
        model.addAttribute("expiry", chequeService.expiry(c, chequeService.settings()));
        model.addAttribute("actions", transitionService.availableActions(ReceivedCheque.RECORD_TYPE, c.lifecycleStatus()));
        Map<String, List<Reason>> reasons = new HashMap<>();
        REASON_LISTS.forEach((action, type) -> reasons.put(action, reasonService.active(type)));
        model.addAttribute("reasons", reasons);
        model.addAttribute("machine", ChequeLifecycles.RECEIVED);
        model.addAttribute("today", LocalDate.now(clock));
        model.addAttribute("approvals", approvalService.forRecord(ReceivedCheque.RECORD_TYPE, id));
        model.addAttribute("statusHistory", auditQueryService.statusHistory(ReceivedCheque.RECORD_TYPE, id));
        model.addAttribute("auditEntries", auditQueryService.recentForEntity(ReceivedCheque.RECORD_TYPE, id, 50));
        model.addAttribute("attachments", attachmentService.list(ReceivedCheque.RECORD_TYPE, id));
        model.addAttribute("categories", AttachmentCategory.values());
        model.addAttribute("canManage", CurrentUser.get().hasPermission(ReceivedPermissions.RECEIVED_MANAGE));
        return "received/view";
    }

    /** Any status change: the engine decides whether it is allowed and whether it needs approval. */
    @PostMapping("/{id}/action/{action}")
    public String action(@PathVariable Long id, @PathVariable String action,
                         @RequestParam(required = false) Long reasonId,
                         @RequestParam(required = false) String note,
                         @RequestParam(required = false) String date,
                         @RequestParam(required = false) String bankCharge,
                         RedirectAttributes redirect) {
        if ("DEPOSIT".equals(action)) {
            return "redirect:/received/deposits/new?chequeId=" + id;
        }
        try {
            String reason = reasonText(action, reasonId, note);
            Map<String, Object> payload = new HashMap<>();
            if (date != null && !date.isBlank()) {
                payload.put(ReceivedChequeLifecycle.PAYLOAD_DATE, date);
            }
            if (bankCharge != null && !bankCharge.isBlank()) {
                payload.put(ReceivedChequeLifecycle.PAYLOAD_CHARGE, bankCharge);
            }
            TransitionOutcome outcome = transitionService.request(ReceivedCheque.RECORD_TYPE, id, action,
                    TransitionInput.of(reason, payload));
            redirect.addFlashAttribute("successMessage", outcome.message());
            if (outcome.done() && "REPLACE".equals(action)) {
                return "redirect:/received/new?replaces=" + id + "&partyId=" + chequeService.get(id).getParty().getId();
            }
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/received/" + id;
    }

    // ---- helpers -------------------------------------------------------------------------------

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

    private String showForm(Model model) {
        model.addAttribute("customers", partyService.selectable(PartyType.CUSTOMER));
        model.addAttribute("bankBranches", bankService.selectableBranches());
        model.addAttribute("branches", branchScope.selectableBranches());
        model.addAttribute("types", ChequeType.values());
        return "received/form";
    }

    private String showBulkForm(Model model) {
        model.addAttribute("customers", partyService.selectable(PartyType.CUSTOMER));
        model.addAttribute("bankBranches", bankService.selectableBranches());
        model.addAttribute("branches", branchScope.selectableBranches());
        return "received/bulk";
    }
}
