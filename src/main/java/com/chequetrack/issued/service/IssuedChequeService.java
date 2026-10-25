package com.chequetrack.issued.service;

import com.chequetrack.chequebook.domain.ChequeLeaf;
import com.chequetrack.chequebook.service.ChequeBookService;
import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.event.Changes;
import com.chequetrack.common.event.StatusChangedEvent;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.issued.IssuedPermissions;
import com.chequetrack.issued.domain.IssuedCheque;
import com.chequetrack.issued.dto.IssuedChequeForm;
import com.chequetrack.issued.repository.IssuedChequeRepository;
import com.chequetrack.lifecycle.domain.IssuedStatus;
import com.chequetrack.organization.scope.BranchScope;
import com.chequetrack.setup.domain.CompanyBankAccount;
import com.chequetrack.setup.domain.CompanySettings;
import com.chequetrack.setup.domain.OpenItem;
import com.chequetrack.setup.domain.OpenItemType;
import com.chequetrack.setup.domain.Party;
import com.chequetrack.setup.service.CompanyAccountService;
import com.chequetrack.setup.service.OpenItemService;
import com.chequetrack.setup.service.PartyService;
import com.chequetrack.setup.service.SettingsService;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writing cheques (drafts with an allocated leaf) and reading them. Status changes after
 * the draft go through the lifecycle engine (IssuedChequeLifecycle).
 */
@Service
public class IssuedChequeService {

    public static final int PAGE_SIZE = 25;
    private static final String VIEW = "hasAuthority('" + IssuedPermissions.ISSUED_VIEW + "')";
    private static final String MANAGE = "hasAuthority('" + IssuedPermissions.ISSUED_MANAGE + "')";

    private final IssuedChequeRepository chequeRepository;
    private final ChequeBookService chequeBookService;
    private final CompanyAccountService accountService;
    private final PartyService partyService;
    private final OpenItemService openItemService;
    private final SettingsService settingsService;
    private final BranchScope branchScope;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public IssuedChequeService(IssuedChequeRepository chequeRepository,
                               ChequeBookService chequeBookService,
                               CompanyAccountService accountService,
                               PartyService partyService,
                               OpenItemService openItemService,
                               SettingsService settingsService,
                               BranchScope branchScope,
                               EntityManager entityManager,
                               ApplicationEventPublisher events,
                               Clock clock) {
        this.chequeRepository = chequeRepository;
        this.chequeBookService = chequeBookService;
        this.accountService = accountService;
        this.partyService = partyService;
        this.openItemService = openItemService;
        this.settingsService = settingsService;
        this.branchScope = branchScope;
        this.entityManager = entityManager;
        this.events = events;
        this.clock = clock;
    }

    // ---- reading -------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public Page<IssuedCheque> search(Collection<IssuedStatus> statuses, Long partyId, Long accountId, LocalDate from,
                                     LocalDate to, String query, int page) {
        Collection<IssuedStatus> st = statuses == null || statuses.isEmpty() ? EnumSet.allOf(IssuedStatus.class) : statuses;
        String q = query == null || query.isBlank() ? "" : "%" + query.trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
        return chequeRepository.search(st, partyId, accountId, from, to, q,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Order.desc("chequeDate"), Sort.Order.desc("id"))));
    }

    /** Supplier PDC register: handed over and not yet paid by the bank, by due date. */
    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public List<IssuedCheque> outstanding(Long partyId) {
        return chequeRepository.findOutstanding(partyId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public IssuedCheque get(Long id) {
        IssuedCheque c = branchScope.require(chequeRepository.findById(id), "Issued cheque", id);
        // load what the page shows (open-in-view is off)
        c.getParty().getName();
        c.getAccount().getBankBranch().getBank().getName();
        c.getLeaf().getBook().getId();
        c.getBills().forEach(b -> b.getBill().getNumber());
        if (c.getPayment() != null) {
            c.getPayment().getNumber();
        }
        return c;
    }

    /** Live reissue of a void or stopped cheque, or null. */
    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public IssuedCheque reissueOf(Long id) {
        return chequeRepository.findReissues(id).stream().findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public CompanySettings settings() {
        return settingsService.current();
    }

    /** Expiry of a cheque that is still out with the payee. */
    public Expiry expiry(IssuedCheque c, CompanySettings settings) {
        LocalDate expiry = c.expiryDate(settings.getChequeValidityDays());
        LocalDate today = LocalDate.now(clock);
        long days = ChronoUnit.DAYS.between(today, expiry);
        long dueIn = ChronoUnit.DAYS.between(today, c.getChequeDate());
        boolean out = c.getStatus() == IssuedStatus.ISSUED;
        return new Expiry(expiry, days, dueIn, out && days < 0, out && days >= 0 && days <= settings.getNearExpiryDays());
    }

    /** Leaf the next cheque on this account would get (shown on the form, not reserved). */
    @Transactional(readOnly = true)
    @PreAuthorize(MANAGE)
    public String nextLeafNumber(Long accountId) {
        if (accountId == null) {
            return null;
        }
        accountService.get(accountId);  // branch check
        ChequeLeaf leaf = chequeBookService.peekNextLeaf(accountId);
        return leaf == null ? null : leaf.getDisplayNumber();
    }

    /** Open bills of a supplier for the form; amounts already linked to the cheque being edited are filled in. */
    @Transactional(readOnly = true)
    @PreAuthorize(MANAGE)
    public List<IssuedChequeForm.Allocation> allocationLines(Long partyId, Map<Long, BigDecimal> linked) {
        List<IssuedChequeForm.Allocation> lines = new ArrayList<>();
        if (partyId == null) {
            return lines;
        }
        for (OpenItem item : openItemService.openForParty(partyId, OpenItemType.BILL)) {
            IssuedChequeForm.Allocation a = new IssuedChequeForm.Allocation();
            a.setOpenItemId(item.getId());
            a.setNumber(item.getNumber());
            a.setOutstanding(item.getOutstanding());
            a.setAmount(linked == null ? null : linked.get(item.getId()));
            lines.add(a);
        }
        return lines;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(MANAGE)
    public IssuedChequeForm editForm(Long id) {
        IssuedCheque c = get(id);
        if (c.getStatus() != IssuedStatus.DRAFT) {
            throw new BusinessException("Only a draft cheque can be changed.");
        }
        IssuedChequeForm form = new IssuedChequeForm();
        form.setId(c.getId());
        form.setVersion(c.getVersion());
        form.setAccountId(c.getAccount().getId());
        form.setPartyId(c.getParty().getId());
        form.setPayeeName(c.getPayeeName());
        form.setAmount(c.getAmount());
        form.setCrossing(c.getCrossing());
        form.setChequeDate(c.getChequeDate());
        form.setNarration(c.getNarration());
        Map<Long, BigDecimal> linked = new HashMap<>();
        c.getBills().forEach(b -> linked.put(b.getBill().getId(), b.getAmount()));
        form.setAllocations(allocationLines(c.getParty().getId(), linked));
        return form;
    }

    /** A new form copying a void or stopped cheque, to write it again on a new leaf. */
    @Transactional(readOnly = true)
    @PreAuthorize(MANAGE)
    public IssuedChequeForm reissueForm(Long id) {
        IssuedCheque old = checkReissuable(id);
        IssuedChequeForm form = new IssuedChequeForm();
        form.setReissueOfId(old.getId());
        form.setAccountId(old.getAccount().getId());
        form.setPartyId(old.getParty().getId());
        form.setPayeeName(old.getPayeeName());
        form.setAmount(old.getAmount());
        form.setCrossing(old.getCrossing());
        LocalDate today = LocalDate.now(clock);
        form.setChequeDate(old.getChequeDate().isBefore(today) ? today : old.getChequeDate());
        form.setNarration(old.getNarration());
        Map<Long, BigDecimal> linked = new HashMap<>();
        old.getBills().forEach(b -> linked.put(b.getBill().getId(), b.getAmount()));
        form.setAllocations(allocationLines(old.getParty().getId(), linked));
        return form;
    }

    // ---- writing -------------------------------------------------------------------------------

    /** Saves a draft on the next unused leaf of the account (locked, so never shared with another user). */
    @Transactional
    @PreAuthorize(MANAGE)
    public IssuedCheque create(IssuedChequeForm form) {
        CompanyBankAccount account = accountService.get(form.getAccountId());  // branch check
        if (!account.isActive()) {
            throw new BusinessException("Account " + account.getLabel() + " is inactive.");
        }
        Party supplier = activeSupplier(form.getPartyId());
        checkDate(form.getChequeDate());
        Long reissueOf = null;
        if (form.getReissueOfId() != null) {
            reissueOf = checkReissuable(form.getReissueOfId()).getId();
        }

        ChequeLeaf leaf = chequeBookService.allocateNextLeaf(account);
        IssuedCheque cheque = IssuedCheque.draft(account, leaf, reissueOf);
        cheque.details(supplier, form.getPayeeName(), form.getAmount(), form.getCrossing(), form.getChequeDate(),
                form.getNarration());
        linkBills(cheque, supplier, form);
        try {
            chequeRepository.saveAndFlush(cheque);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(reissueOf != null
                    ? "This cheque was reissued by someone else at the same moment."
                    : "Leaf " + cheque.getChequeNumber() + " was taken at the same moment. Save again to get the next leaf.");
        }
        events.publishEvent(new StatusChangedEvent(IssuedCheque.RECORD_TYPE, cheque.getId(), cheque.getCompanyId(),
                cheque.getBranchId(), null, IssuedStatus.DRAFT.name(),
                reissueOf == null ? "Written on leaf " + cheque.getChequeNumber() : "Reissue on leaf " + cheque.getChequeNumber()));
        events.publishEvent(AuditEvent.of(reissueOf == null ? "ISSUED_CHEQUE_CREATED" : "ISSUED_CHEQUE_REISSUED",
                IssuedCheque.RECORD_TYPE, cheque.getId(),
                "Wrote cheque " + cheque.getChequeNumber() + " on " + account.getLabel() + " to " + cheque.getPayeeName()
                        + ", Tk " + cheque.getAmount().toPlainString() + " dated " + cheque.getChequeDate()
                        + (cheque.getBills().isEmpty() ? "" : " for " + cheque.getBills().size() + " bill(s)")));
        return cheque;
    }

    /** Changes a draft. Account and leaf stay. */
    @Transactional
    @PreAuthorize(MANAGE)
    public void update(Long id, IssuedChequeForm form) {
        IssuedCheque cheque = get(id);
        cheque.checkVersion(form.getVersion());
        Party supplier = activeSupplier(form.getPartyId());
        checkDate(form.getChequeDate());
        Changes changes = new Changes()
                .add("supplier", cheque.getParty().getLabel(), supplier.getLabel())
                .add("payee", cheque.getPayeeName(), form.getPayeeName() == null || form.getPayeeName().isBlank()
                        ? supplier.getName() : form.getPayeeName().trim())
                .add("amount", cheque.getAmount().stripTrailingZeros().toPlainString(),
                        form.getAmount().stripTrailingZeros().toPlainString())
                .add("crossing", cheque.getCrossing().getLabel(), form.getCrossing().getLabel())
                .add("date", cheque.getChequeDate(), form.getChequeDate())
                .add("narration", cheque.getNarration(), form.getNarration());
        cheque.details(supplier, form.getPayeeName(), form.getAmount(), form.getCrossing(), form.getChequeDate(),
                form.getNarration());
        cheque.clearBills();
        entityManager.flush();  // delete old bill links before inserting the same bills again
        linkBills(cheque, supplier, form);
        if (!changes.isEmpty()) {
            events.publishEvent(AuditEvent.of("ISSUED_CHEQUE_UPDATED", IssuedCheque.RECORD_TYPE, cheque.getId(),
                    "Updated draft cheque " + cheque.getChequeNumber(), changes));
        }
    }

    // ---- helpers -------------------------------------------------------------------------------

    private Party activeSupplier(Long partyId) {
        Party party = partyService.get(partyId);
        if (!party.isActive() || !party.getPartyType().isSupplier()) {
            throw new BusinessException("Choose an active supplier.");
        }
        return party;
    }

    private void checkDate(LocalDate chequeDate) {
        int validity = settingsService.current().getChequeValidityDays();
        if (chequeDate.plusDays(validity).isBefore(LocalDate.now(clock))) {
            throw new BusinessException("A cheque dated " + chequeDate + " is already stale (older than " + validity
                    + " days); the bank would refuse it.");
        }
    }

    private IssuedCheque checkReissuable(Long id) {
        IssuedCheque old = get(id);
        if (!old.isReissuable()) {
            throw new BusinessException("Only void or stopped cheques can be reissued. Cheque " + old.getChequeNumber()
                    + " is " + old.getStatus().getLabel().toLowerCase() + ".");
        }
        if (chequeRepository.alreadyReissued(old.getId())) {
            throw new BusinessException("Cheque " + old.getChequeNumber() + " has already been reissued.");
        }
        return old;
    }

    private void linkBills(IssuedCheque cheque, Party supplier, IssuedChequeForm form) {
        BigDecimal total = BigDecimal.ZERO;
        Set<Long> seen = new HashSet<>();
        for (IssuedChequeForm.Allocation a : form.getAllocations()) {
            if (a.getOpenItemId() == null || a.getAmount() == null || a.getAmount().signum() == 0 || !seen.add(a.getOpenItemId())) {
                continue;
            }
            OpenItem bill = openItemService.get(a.getOpenItemId());
            if (bill.getItemType() != OpenItemType.BILL || !bill.getParty().getId().equals(supplier.getId())) {
                throw new BusinessException("Bill " + bill.getNumber() + " does not belong to " + supplier.getName() + ".");
            }
            if (a.getAmount().compareTo(bill.getOutstanding()) > 0) {
                throw new BusinessException("Bill " + bill.getNumber() + " has only Tk " + bill.getOutstanding().toPlainString()
                        + " outstanding.");
            }
            total = total.add(a.getAmount());
            cheque.linkBill(bill, a.getAmount());
        }
        if (total.compareTo(cheque.getAmount()) > 0) {
            throw new BusinessException("The bill amounts (Tk " + total.toPlainString() + ") are more than the cheque amount.");
        }
    }

    /**
     * Dates of a cheque with the payee.
     * @param daysLeft days until it expires; {@code dueIn} days until its date (negative = past)
     */
    public record Expiry(LocalDate date, long daysLeft, long dueIn, boolean stale, boolean nearExpiry) {
    }
}
