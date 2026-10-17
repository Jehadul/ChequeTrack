package com.chequetrack.chequebook.web;

import com.chequetrack.audit.service.AuditQueryService;
import com.chequetrack.chequebook.ChequeBookPermissions;
import com.chequetrack.chequebook.domain.BookStatus;
import com.chequetrack.chequebook.domain.ChequeBook;
import com.chequetrack.chequebook.domain.ChequeLeaf;
import com.chequetrack.chequebook.dto.BookForm;
import com.chequetrack.chequebook.service.ChequeBookService;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.setup.domain.ReasonType;
import com.chequetrack.setup.service.CompanyAccountService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDate;

@Controller
@RequestMapping("/chequebooks")
public class ChequeBookController {

    private static final String FORM = "chequebook/form";

    private final ChequeBookService bookService;
    private final CompanyAccountService accountService;
    private final ReasonService reasonService;
    private final AuditQueryService auditQueryService;
    private final Clock clock;

    public ChequeBookController(ChequeBookService bookService,
                                CompanyAccountService accountService,
                                ReasonService reasonService,
                                AuditQueryService auditQueryService,
                                Clock clock) {
        this.bookService = bookService;
        this.accountService = accountService;
        this.reasonService = reasonService;
        this.auditQueryService = auditQueryService;
        this.clock = clock;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long accountId,
                       @RequestParam(required = false) BookStatus status,
                       Model model) {
        model.addAttribute("rows", bookService.search(accountId, status));
        model.addAttribute("accounts", accountService.listVisible());
        model.addAttribute("statuses", BookStatus.values());
        model.addAttribute("accountId", accountId);
        model.addAttribute("status", status);
        return "chequebook/list";
    }

    @GetMapping("/stock")
    public String stock(Model model) {
        model.addAttribute("stock", bookService.stock());
        return "chequebook/stock";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('" + ChequeBookPermissions.CHEQUEBOOK_MANAGE + "')")
    public String newForm(@RequestParam(required = false) Long accountId, Model model) {
        BookForm form = new BookForm();
        form.setAccountId(accountId);
        form.setReceivedDate(LocalDate.now(clock));
        model.addAttribute("form", form);
        return showForm(model);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") BookForm form, BindingResult binding, Model model,
                         RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                ChequeBook book = bookService.create(form);
                redirect.addFlashAttribute("successMessage",
                        "Cheque book " + book.getRangeLabel() + " added with " + book.getLeafCount() + " leaves.");
                return "redirect:/chequebooks/" + book.getId();
            } catch (BusinessException ex) {
                binding.reject("book", ex.getMessage());
            }
        }
        return showForm(model);
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("d", bookService.detail(id));
        model.addAttribute("voidReasons", reasonService.active(ReasonType.VOID));
        model.addAttribute("statusHistory", auditQueryService.statusHistory(ChequeBookService.BOOK, id));
        model.addAttribute("auditEntries", auditQueryService.recentForEntity(ChequeBookService.BOOK, id, 50));
        return "chequebook/view";
    }

    @PostMapping("/leaves/{leafId}/void")
    public String voidLeaf(@PathVariable Long leafId, @RequestParam(required = false) Long reasonId,
                           @RequestParam(required = false) String note, RedirectAttributes redirect) {
        try {
            ChequeLeaf leaf = bookService.voidLeaf(leafId, reasonId, note);
            redirect.addFlashAttribute("successMessage", "Leaf " + leaf.getDisplayNumber() + " voided.");
            return "redirect:/chequebooks/" + leaf.getBook().getId();
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/chequebooks/" + bookIdOfLeaf(leafId);
        }
    }

    @PostMapping("/{id}/close")
    public String close(@PathVariable Long id, @RequestParam(required = false) BookStatus status,
                        @RequestParam(required = false) String reason, RedirectAttributes redirect) {
        try {
            bookService.close(id, status, reason);
            redirect.addFlashAttribute("successMessage",
                    status == BookStatus.LOST ? "Book reported lost." : "Book cancelled.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/chequebooks/" + id;
    }

    private String showForm(Model model) {
        model.addAttribute("accounts", accountService.selectable());
        return FORM;
    }

    /** Book of a leaf for the redirect after an error; falls back to the list. */
    private String bookIdOfLeaf(Long leafId) {
        try {
            return String.valueOf(bookService.leafBookId(leafId));
        } catch (RuntimeException ex) {
            return "";
        }
    }
}
