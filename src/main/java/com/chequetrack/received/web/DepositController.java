package com.chequetrack.received.web;

import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.received.ReceivedPermissions;
import com.chequetrack.received.domain.DepositBatch;
import com.chequetrack.received.domain.ReceivedCheque;
import com.chequetrack.received.dto.DepositForm;
import com.chequetrack.received.service.DepositService;
import com.chequetrack.setup.service.CompanyAccountService;
import jakarta.validation.Valid;
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
import java.util.ArrayList;
import java.util.List;

/** Deposit batches and their printable slips. */
@Controller
@RequestMapping("/received/deposits")
public class DepositController {

    private final DepositService depositService;
    private final CompanyAccountService accountService;
    private final Clock clock;

    public DepositController(DepositService depositService, CompanyAccountService accountService, Clock clock) {
        this.depositService = depositService;
        this.accountService = accountService;
        this.clock = clock;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("batches", depositService.recent());
        return "received/deposits";
    }

    /** Cheques due by the date, all ticked (or only {@code chequeId} when coming from one cheque). */
    @GetMapping("/new")
    @PreAuthorize("hasAuthority('" + ReceivedPermissions.RECEIVED_MANAGE + "')")
    public String newForm(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          @RequestParam(required = false) Long chequeId, Model model) {
        DepositForm form = new DepositForm();
        form.setDepositDate(date == null ? LocalDate.now(clock) : date);
        List<ReceivedCheque> due = depositService.dueForDeposit(form.getDepositDate());
        List<Long> ticked = new ArrayList<>();
        due.stream().filter(c -> chequeId == null || c.getId().equals(chequeId)).forEach(c -> ticked.add(c.getId()));
        form.setChequeIds(ticked);
        model.addAttribute("form", form);
        model.addAttribute("chequeId", chequeId);
        return showForm(model, due);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") DepositForm form, BindingResult binding, Model model,
                         RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                DepositBatch batch = depositService.create(form);
                redirect.addFlashAttribute("successMessage", "Deposit " + batch.getNumber() + " recorded with "
                        + batch.getChequeCount() + " cheque(s). Print the slip below.");
                return "redirect:/received/deposits/" + batch.getId();
            } catch (BusinessException ex) {
                binding.reject("deposit", ex.getMessage());
            }
        }
        return showForm(model, depositService.dueForDeposit(form.getDepositDate() == null ? LocalDate.now(clock) : form.getDepositDate()));
    }

    /** The batch with its printable deposit slip. */
    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("batch", depositService.get(id));
        model.addAttribute("cheques", depositService.chequesOf(id));
        return "received/deposit-slip";
    }

    private String showForm(Model model, List<ReceivedCheque> due) {
        model.addAttribute("due", due);
        model.addAttribute("dueTotal", due.stream().map(ReceivedCheque::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("accounts", accountService.selectable());
        return "received/deposit-form";
    }
}
