package com.chequetrack.received.service;

import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.event.StatusChangedEvent;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.lifecycle.domain.ReceivedStatus;
import com.chequetrack.organization.scope.BranchScope;
import com.chequetrack.received.ReceivedPermissions;
import com.chequetrack.received.domain.ChequeType;
import com.chequetrack.received.domain.ReceivedCheque;
import com.chequetrack.received.dto.BulkChequeForm;
import com.chequetrack.received.dto.ChequeForm;
import com.chequetrack.received.repository.ReceivedChequeRepository;
import com.chequetrack.security.service.CurrentUser;
import com.chequetrack.setup.domain.BankBranch;
import com.chequetrack.setup.domain.CompanySettings;
import com.chequetrack.setup.domain.OpenItem;
import com.chequetrack.setup.domain.OpenItemType;
import com.chequetrack.setup.domain.Party;
import com.chequetrack.setup.service.BankService;
import com.chequetrack.setup.service.OpenItemService;
import com.chequetrack.setup.service.PartyService;
import com.chequetrack.setup.service.SettingsService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Entering received cheques and reading them. Status changes go through the lifecycle engine. */
@Service
public class ReceivedChequeService {

    public static final int PAGE_SIZE = 25;
    private static final String VIEW = "hasAuthority('" + ReceivedPermissions.RECEIVED_VIEW + "')";
    private static final String MANAGE = "hasAuthority('" + ReceivedPermissions.RECEIVED_MANAGE + "')";

    private final ReceivedChequeRepository chequeRepository;
    private final PartyService partyService;
    private final BankService bankService;
    private final OpenItemService openItemService;
    private final SettingsService settingsService;
    private final BranchScope branchScope;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ReceivedChequeService(ReceivedChequeRepository chequeRepository,
                                 PartyService partyService,
                                 BankService bankService,
                                 OpenItemService openItemService,
                                 SettingsService settingsService,
                                 BranchScope branchScope,
                                 ApplicationEventPublisher events,
                                 Clock clock) {
        this.chequeRepository = chequeRepository;
        this.partyService = partyService;
        this.bankService = bankService;
        this.openItemService = openItemService;
        this.settingsService = settingsService;
        this.branchScope = branchScope;
        this.events = events;
        this.clock = clock;
    }

    // ---- reading -------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public Page<ReceivedCheque> search(Collection<ReceivedStatus> statuses, Long partyId, LocalDate from, LocalDate to,
                                       String query, int page) {
        Collection<ReceivedStatus> st = statuses == null || statuses.isEmpty() ? EnumSet.allOf(ReceivedStatus.class) : statuses;
        String q = query == null || query.isBlank() ? "" : "%" + query.trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
        return chequeRepository.search(st, partyId, from, to, q,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Order.asc("chequeDate"), Sort.Order.asc("id"))));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public ReceivedCheque get(Long id) {
        ReceivedCheque c = branchScope.require(chequeRepository.findById(id), "Received cheque", id);
        // load what the page shows (open-in-view is off)
        c.getParty().getName();
        c.getDrawerBankBranch().getBank().getName();
        c.getInvoices().forEach(i -> i.getInvoice().getNumber());
        if (c.getReceipt() != null) {
            c.getReceipt().getNumber();
        }
        if (c.getDepositBatch() != null) {
            c.getDepositBatch().getAccount().getBankBranch().getBank().getName();
        }
        return c;
    }

    /** Expiry information shown as badges. */
    public Expiry expiry(ReceivedCheque c, CompanySettings settings) {
        LocalDate expiry = c.expiryDate(settings.getChequeValidityDays());
        long days = ChronoUnit.DAYS.between(LocalDate.now(clock), expiry);
        boolean relevant = c.isAwaitingBank();
        return new Expiry(expiry, days, relevant && days < 0, relevant && days >= 0 && days <= settings.getNearExpiryDays());
    }

    @Transactional(readOnly = true)
    public CompanySettings settings() {
        return settingsService.current();
    }

    /** Open invoices of a customer for the entry form, each with an empty amount. */
    @Transactional(readOnly = true)
    @PreAuthorize(MANAGE)
    public List<ChequeForm.Allocation> allocationLines(Long partyId) {
        List<ChequeForm.Allocation> lines = new ArrayList<>();
        if (partyId == null) {
            return lines;
        }
        for (OpenItem item : openItemService.openForParty(partyId, OpenItemType.INVOICE)) {
            ChequeForm.Allocation a = new ChequeForm.Allocation();
            a.setOpenItemId(item.getId());
            a.setNumber(item.getNumber());
            a.setOutstanding(item.getOutstanding());
            lines.add(a);
        }
        return lines;
    }

    // ---- entry ---------------------------------------------------------------------------------

    @Transactional
    @PreAuthorize(MANAGE)
    public ReceivedCheque create(ChequeForm form) {
        Party customer = activeCustomer(form.getPartyId());
        BankBranch bankBranch = activeBankBranch(form.getDrawerBankBranchId());
        checkDates(form.getChequeType(), form.getChequeDate(), form.getReceivedDate());
        Long branchId = branchScope.branchForNewRecord(form.getBranchId());
        Long companyId = CurrentUser.get().getCompanyId();

        ReceivedCheque cheque = ReceivedCheque.create(companyId, branchId, customer, form.getChequeType(),
                form.getChequeNumber(), form.getChequeDate(), form.getAmount(), bankBranch, form.getDrawerAccountNumber(),
                form.getDrawerName(), form.getReceivedDate(), form.getNarration(), replaces(form.getReplacesId()));
        checkNotDuplicate(cheque);
        linkInvoices(cheque, customer, form);
        save(cheque);
        events.publishEvent(AuditEvent.of("RECEIVED_CHEQUE_CREATED", ReceivedCheque.RECORD_TYPE, cheque.getId(),
                "Received " + cheque.getChequeType().getLabel().toLowerCase() + " cheque " + cheque.getChequeNumber()
                        + " from " + customer.getName() + ", Tk " + cheque.getAmount().toPlainString()
                        + (cheque.getInvoices().isEmpty() ? "" : " for " + cheque.getInvoices().size() + " invoice(s)")));
        return cheque;
    }

    /** A series of cheques, e.g. 12 monthly post-dated cheques; all saved or none. */
    @Transactional
    @PreAuthorize(MANAGE)
    public List<ReceivedCheque> createSeries(BulkChequeForm form) {
        Party customer = activeCustomer(form.getPartyId());
        BankBranch bankBranch = activeBankBranch(form.getDrawerBankBranchId());
        Long branchId = branchScope.branchForNewRecord(form.getBranchId());
        Long companyId = CurrentUser.get().getCompanyId();
        int width = form.getFirstNumber().length();
        BigDecimal first = new BigDecimal(form.getFirstNumber());

        List<ReceivedCheque> created = new ArrayList<>();
        Set<String> numbers = new HashSet<>();
        for (int i = 0; i < form.getCount(); i++) {
            String number = String.format("%0" + width + "d", first.add(BigDecimal.valueOf(i)).toBigInteger());
            if (number.length() > 20 || !numbers.add(number)) {
                throw new BusinessException("Cheque numbers would exceed 20 digits.");
            }
            LocalDate date = form.getFirstDate().plusMonths((long) i * form.getEveryMonths());
            ChequeType type = date.isAfter(LocalDate.now(clock)) ? ChequeType.POST_DATED
                    : (form.getChequeType() == ChequeType.SECURITY ? ChequeType.SECURITY : ChequeType.CURRENT);
            checkDates(type, date, form.getReceivedDate());
            ReceivedCheque cheque = ReceivedCheque.create(companyId, branchId, customer, type, number, date,
                    form.getAmount(), bankBranch, form.getDrawerAccountNumber(), form.getDrawerName(),
                    form.getReceivedDate(), form.getNarration(), null);
            checkNotDuplicate(cheque);
            save(cheque);
            created.add(cheque);
        }
        events.publishEvent(AuditEvent.of("RECEIVED_CHEQUE_SERIES_CREATED", "Party", customer.getId(),
                "Received " + created.size() + " cheques " + created.get(0).getChequeNumber() + "–"
                        + created.get(created.size() - 1).getChequeNumber() + " from " + customer.getName()
                        + ", Tk " + form.getAmount().toPlainString() + " each"));
        return created;
    }

    // ---- helpers -------------------------------------------------------------------------------

    private void save(ReceivedCheque cheque) {
        try {
            chequeRepository.saveAndFlush(cheque);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("Cheque " + cheque.getChequeNumber() + " of this bank and account was entered at the same moment by someone else.");
        }
        events.publishEvent(new StatusChangedEvent(ReceivedCheque.RECORD_TYPE, cheque.getId(), cheque.getCompanyId(),
                cheque.getBranchId(), null, ReceivedStatus.IN_HAND.name(), "Received on " + cheque.getReceivedDate()));
    }

    private Party activeCustomer(Long partyId) {
        Party party = partyService.get(partyId);
        if (!party.isActive() || !party.getPartyType().isCustomer()) {
            throw new BusinessException("Choose an active customer.");
        }
        return party;
    }

    private BankBranch activeBankBranch(Long id) {
        BankBranch bb = bankService.getBranch(id);
        if (!bb.isActive() || !bb.getBank().isActive()) {
            throw new BusinessException("The drawer's bank branch is inactive.");
        }
        return bb;
    }

    private void checkDates(ChequeType type, LocalDate chequeDate, LocalDate receivedDate) {
        LocalDate today = LocalDate.now(clock);
        if (receivedDate.isAfter(today)) {
            throw new BusinessException("The received date cannot be in the future.");
        }
        if (type == ChequeType.CURRENT && chequeDate.isAfter(today)) {
            throw new BusinessException("A cheque dated in the future is post-dated. Choose the type 'Post-dated'.");
        }
        int validity = settingsService.current().getChequeValidityDays();
        if (chequeDate.plusDays(validity).isBefore(today)) {
            throw new BusinessException("This cheque is older than " + validity + " days (stale); the bank will not accept it.");
        }
    }

    private void checkNotDuplicate(ReceivedCheque c) {
        if (chequeRepository.identityTaken(c.getCompanyId(), c.getDrawerBank().getId(), c.getDrawerAccountNumber(), c.getChequeNumber())) {
            throw new BusinessException("Cheque " + c.getChequeNumber() + " of " + c.getDrawerBank().getName()
                    + " account " + c.getDrawerAccountNumber() + " has already been entered.");
        }
    }

    private void linkInvoices(ReceivedCheque cheque, Party customer, ChequeForm form) {
        BigDecimal total = BigDecimal.ZERO;
        Set<Long> seen = new HashSet<>();
        for (ChequeForm.Allocation a : form.getAllocations()) {
            if (a.getOpenItemId() == null || a.getAmount() == null || a.getAmount().signum() == 0) {
                continue;
            }
            if (!seen.add(a.getOpenItemId())) {
                continue;
            }
            OpenItem invoice = openItemService.get(a.getOpenItemId());
            if (invoice.getItemType() != OpenItemType.INVOICE || !invoice.getParty().getId().equals(customer.getId())) {
                throw new BusinessException("Invoice " + invoice.getNumber() + " does not belong to " + customer.getName() + ".");
            }
            if (a.getAmount().compareTo(invoice.getOutstanding()) > 0) {
                throw new BusinessException("Invoice " + invoice.getNumber() + " has only Tk " + invoice.getOutstanding().toPlainString()
                        + " outstanding.");
            }
            total = total.add(a.getAmount());
            cheque.linkInvoice(invoice, a.getAmount());
        }
        if (total.compareTo(cheque.getAmount()) > 0) {
            throw new BusinessException("The invoice amounts (Tk " + total.toPlainString() + ") are more than the cheque amount.");
        }
    }

    private Long replaces(Long replacesId) {
        if (replacesId == null) {
            return null;
        }
        ReceivedCheque old = get(replacesId);
        if (old.getStatus() != ReceivedStatus.REPLACED) {
            throw new BusinessException("Cheque " + old.getChequeNumber() + " has not been marked as replaced.");
        }
        return old.getId();
    }

    /** Expiry of a cheque: last valid day, days left, stale, near expiry. */
    public record Expiry(LocalDate date, long daysLeft, boolean stale, boolean nearExpiry) {
    }
}
