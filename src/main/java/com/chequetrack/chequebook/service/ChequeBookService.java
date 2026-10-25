package com.chequetrack.chequebook.service;

import com.chequetrack.chequebook.ChequeBookPermissions;
import com.chequetrack.chequebook.domain.BookStatus;
import com.chequetrack.chequebook.domain.ChequeBook;
import com.chequetrack.chequebook.domain.ChequeLeaf;
import com.chequetrack.chequebook.domain.LeafStatus;
import com.chequetrack.chequebook.dto.BookForm;
import com.chequetrack.chequebook.repository.ChequeBookRepository;
import com.chequetrack.chequebook.repository.ChequeLeafRepository;
import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.event.StatusChangedEvent;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.organization.scope.BranchScope;
import com.chequetrack.security.service.CurrentUser;
import com.chequetrack.setup.domain.CompanyBankAccount;
import com.chequetrack.setup.domain.Reason;
import com.chequetrack.setup.domain.ReasonType;
import com.chequetrack.setup.service.CompanyAccountService;
import com.chequetrack.setup.service.ReasonService;
import com.chequetrack.setup.service.SettingsService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cheque books and their leaves. Rules:
 * <ul>
 *   <li>a book's numbers may not overlap another book of the same account and series
 *       (checked here, and guaranteed by a unique index on the leaves);</li>
 *   <li>a book is FINISHED automatically when it has no unused or allocated leaf left;</li>
 *   <li>cancelling or losing a book closes its unused leaves; every change has a reason.</li>
 * </ul>
 */
@Service
public class ChequeBookService {

    public static final String BOOK = "ChequeBook";
    public static final String LEAF = "ChequeLeaf";
    private static final String VIEW = "hasAuthority('" + ChequeBookPermissions.CHEQUEBOOK_VIEW + "')";
    private static final String MANAGE = "hasAuthority('" + ChequeBookPermissions.CHEQUEBOOK_MANAGE + "')";

    private final ChequeBookRepository bookRepository;
    private final ChequeLeafRepository leafRepository;
    private final CompanyAccountService accountService;
    private final ReasonService reasonService;
    private final SettingsService settingsService;
    private final BranchScope branchScope;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ChequeBookService(ChequeBookRepository bookRepository,
                             ChequeLeafRepository leafRepository,
                             CompanyAccountService accountService,
                             ReasonService reasonService,
                             SettingsService settingsService,
                             BranchScope branchScope,
                             ApplicationEventPublisher events,
                             Clock clock) {
        this.bookRepository = bookRepository;
        this.leafRepository = leafRepository;
        this.accountService = accountService;
        this.reasonService = reasonService;
        this.settingsService = settingsService;
        this.branchScope = branchScope;
        this.events = events;
        this.clock = clock;
    }

    // ---- reading -------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public List<BookRow> search(Long accountId, BookStatus status) {
        List<ChequeBook> books = bookRepository.search(accountId, status);
        Map<Long, Map<LeafStatus, Long>> counts = countsByBook(books.stream().map(ChequeBook::getId).toList());
        return books.stream().map(b -> new BookRow(b, counts.getOrDefault(b.getId(), Map.of()))).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public ChequeBook get(Long id) {
        ChequeBook book = branchScope.require(bookRepository.findById(id), "Cheque book", id);
        book.getAccount().getBankBranch().getBank().getName();  // load for the page (open-in-view is off)
        return book;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public BookDetail detail(Long id) {
        ChequeBook book = get(id);
        List<ChequeLeaf> leaves = leafRepository.findByBook(id);
        Map<LeafStatus, Long> counts = new EnumMap<>(LeafStatus.class);
        leaves.forEach(l -> counts.merge(l.getStatus(), 1L, Long::sum));
        return new BookDetail(book, leaves, counts, leafRepository.findSkipped(id));
    }

    /** Book id of a leaf the user may see. */
    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public Long leafBookId(Long leafId) {
        return branchScope.require(leafRepository.findById(leafId), "Cheque leaf", leafId).getBook().getId();
    }

    /** Stock per bank account, counting leaves of active books only. */
    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public List<AccountStock> stock() {
        Map<Long, Map<LeafStatus, Long>> perAccount = new HashMap<>();
        for (Object[] row : leafRepository.countActiveByAccountAndStatus()) {
            perAccount.computeIfAbsent((Long) row[0], k -> new EnumMap<>(LeafStatus.class))
                    .put((LeafStatus) row[1], (Long) row[2]);
        }
        Map<Long, Long> activeBooks = bookRepository.search(null, BookStatus.ACTIVE).stream()
                .collect(Collectors.groupingBy(b -> b.getAccount().getId(), Collectors.counting()));
        Map<Long, Long> skipped = leafRepository.findSkipped(null).stream()
                .collect(Collectors.groupingBy(ChequeLeaf::getAccountId, Collectors.counting()));
        int lowStock = settingsService.current().getLowStockLeaves();

        return accountService.listVisible().stream()
                .filter(CompanyBankAccount::isActive)
                .map(a -> {
                    Map<LeafStatus, Long> c = perAccount.getOrDefault(a.getId(), Map.of());
                    long unused = c.getOrDefault(LeafStatus.UNUSED, 0L);
                    return new AccountStock(a, activeBooks.getOrDefault(a.getId(), 0L), unused,
                            c.getOrDefault(LeafStatus.ALLOCATED, 0L), c.getOrDefault(LeafStatus.USED, 0L),
                            c.getOrDefault(LeafStatus.VOID, 0L), unused < lowStock, lowStock,
                            skipped.getOrDefault(a.getId(), 0L));
                })
                .toList();
    }

    // ---- changes -------------------------------------------------------------------------------

    @Transactional
    @PreAuthorize(MANAGE)
    public ChequeBook create(BookForm form) {
        CompanyBankAccount account = accountService.get(form.getAccountId());  // branch check
        if (!account.isActive()) {
            throw new BusinessException("Account " + account.getLabel() + " is inactive.");
        }
        ChequeBook book = ChequeBook.create(account, form.getPrefix(), form.getFirstNumber(), form.getLeafCount(),
                form.getNumberWidth(), form.getReceivedDate(), form.getNotes());
        if (bookRepository.rangeOverlaps(account.getId(), book.getPrefix(), book.getFirstNumber(), book.getLastNumber())) {
            throw new BusinessException("Leaves " + book.getRangeLabel() + " overlap a cheque book already registered for "
                    + account.getLabel() + ".");
        }
        bookRepository.saveAndFlush(book);
        int created;
        try {
            created = leafRepository.createLeaves(book.getId(), clock.instant(), CurrentUser.get().getUsername());
        } catch (DataIntegrityViolationException ex) {
            // Another user registered an overlapping book at the same moment
            throw new BusinessException("Some of these leaf numbers already exist for this account. Reload and check the books.");
        }
        events.publishEvent(new StatusChangedEvent(BOOK, book.getId(), book.getCompanyId(), book.getBranchId(),
                null, BookStatus.ACTIVE.name(), "Received with " + created + " leaves"));
        events.publishEvent(AuditEvent.of("CHEQUE_BOOK_CREATED", BOOK, book.getId(),
                "Added cheque book " + book.getRangeLabel() + " (" + created + " leaves) for " + account.getLabel()));
        return book;
    }

    /** Voids an unused leaf (damaged, spoiled). Needs a reason from the VOID list. */
    @Transactional
    @PreAuthorize(MANAGE)
    public ChequeLeaf voidLeaf(Long leafId, Long reasonId, String note) {
        ChequeLeaf leaf = branchScope.require(leafRepository.findById(leafId), "Cheque leaf", leafId);
        ChequeBook book = leaf.getBook();
        if (book.getStatus() == BookStatus.CANCELLED || book.getStatus() == BookStatus.LOST) {
            throw new BusinessException("This book is " + book.getStatus().name().toLowerCase() + ".");
        }
        String reason = reasonText(ReasonType.VOID, reasonId, note);
        leaf.voidUnused();
        events.publishEvent(new StatusChangedEvent(LEAF, leaf.getId(), leaf.getCompanyId(), leaf.getBranchId(),
                LeafStatus.UNUSED.name(), LeafStatus.VOID.name(), reason));
        events.publishEvent(AuditEvent.of("LEAF_VOIDED", BOOK, book.getId(),
                "Voided unused leaf " + leaf.getDisplayNumber() + ": " + reason));
        finishIfEmpty(book);
        return leaf;
    }

    /** Book cancelled (e.g. account closed) or lost: its unused leaves are closed too. */
    @Transactional
    @PreAuthorize(MANAGE)
    public void close(Long bookId, BookStatus newStatus, String reason) {
        if (newStatus != BookStatus.CANCELLED && newStatus != BookStatus.LOST) {
            throw new BusinessException("Choose cancel or lost.");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Enter the reason.");
        }
        if (reason.length() > 300) {
            throw new BusinessException("The reason must be at most 300 characters.");
        }
        ChequeBook book = get(bookId);
        if (leafRepository.countByBookAndStatusIn(bookId, EnumSet.of(LeafStatus.ALLOCATED)) > 0) {
            throw new BusinessException("A leaf of this book is being used for a cheque right now. Try again in a moment.");
        }
        BookStatus from = book.getStatus();
        book.close(newStatus);
        LeafStatus leafStatus = newStatus == BookStatus.LOST ? LeafStatus.LOST : LeafStatus.CANCELLED;
        int closed = leafRepository.closeUnused(bookId, leafStatus, clock.instant(), CurrentUser.get().getUsername());
        String text = reason.trim() + " (" + closed + " unused leaves marked " + leafStatus.name().toLowerCase() + ")";
        events.publishEvent(new StatusChangedEvent(BOOK, book.getId(), book.getCompanyId(), book.getBranchId(),
                from.name(), newStatus.name(), text));
        events.publishEvent(AuditEvent.of(newStatus == BookStatus.LOST ? "CHEQUE_BOOK_LOST" : "CHEQUE_BOOK_CANCELLED",
                BOOK, book.getId(), (newStatus == BookStatus.LOST ? "Reported lost: " : "Cancelled: ")
                        + book.getRangeLabel() + " – " + text));
    }

    /**
     * Takes the next unused leaf of an account for a new cheque (M9) and marks it ALLOCATED.
     * Must run inside the caller's transaction: the row lock is held until it commits, and a
     * second user at the same moment gets the following leaf.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ChequeLeaf allocateNextLeaf(CompanyBankAccount account) {
        ChequeLeaf leaf = leafRepository.lockNextUnused(account.getId())
                .orElseThrow(() -> new BusinessException("No unused cheque leaf left for " + account.getLabel()
                        + ". Add a cheque book first."));
        leaf.allocate();
        events.publishEvent(new StatusChangedEvent(LEAF, leaf.getId(), leaf.getCompanyId(), leaf.getBranchId(),
                LeafStatus.UNUSED.name(), LeafStatus.ALLOCATED.name(), "Allocated to a new cheque"));
        return leaf;
    }

    /** The leaf a new cheque on this account would get now, or null (for the form; not reserved). */
    @Transactional(readOnly = true)
    public ChequeLeaf peekNextLeaf(Long accountId) {
        ChequeLeaf leaf = leafRepository.peekNextUnused(accountId).orElse(null);
        if (leaf != null) {
            leaf.getBook().getNumberWidth();
        }
        return leaf;
    }

    /**
     * Marks the book FINISHED when nothing is left to issue. Also called by the
     * issued-cheque module (M9) after using a leaf.
     */
    @Transactional
    public void finishIfEmpty(ChequeBook book) {
        if (book.getStatus() == BookStatus.ACTIVE
                && leafRepository.countByBookAndStatusIn(book.getId(), EnumSet.of(LeafStatus.UNUSED, LeafStatus.ALLOCATED)) == 0) {
            book.markFinished();
            events.publishEvent(new StatusChangedEvent(BOOK, book.getId(), book.getCompanyId(), book.getBranchId(),
                    BookStatus.ACTIVE.name(), BookStatus.FINISHED.name(), "No unused leaves left"));
        }
    }

    // ---- helpers -------------------------------------------------------------------------------

    private String reasonText(ReasonType type, Long reasonId, String note) {
        if (reasonId == null) {
            throw new BusinessException("Choose a reason.");
        }
        Reason reason = reasonService.get(reasonId);
        if (reason.getReasonType() != type || !reason.isActive()) {
            throw new BusinessException("Choose a valid reason.");
        }
        String extra = note == null || note.isBlank() ? "" : " – " + note.trim();
        if (extra.length() > 200) {
            throw new BusinessException("The note must be at most 200 characters.");
        }
        return reason.getDescription() + extra;
    }

    private Map<Long, Map<LeafStatus, Long>> countsByBook(List<Long> bookIds) {
        Map<Long, Map<LeafStatus, Long>> result = new HashMap<>();
        if (bookIds.isEmpty()) {
            return result;
        }
        for (Object[] row : leafRepository.countByBookAndStatus(bookIds)) {
            result.computeIfAbsent((Long) row[0], k -> new EnumMap<>(LeafStatus.class))
                    .put((LeafStatus) row[1], (Long) row[2]);
        }
        return result;
    }

    /** A book in a list, with its leaf counts by status. */
    public record BookRow(ChequeBook book, Map<LeafStatus, Long> counts) {
        public long count(String status) {
            return counts.getOrDefault(LeafStatus.valueOf(status), 0L);
        }
    }

    /** A book with all its leaves and the skipped ones. */
    public record BookDetail(ChequeBook book, List<ChequeLeaf> leaves, Map<LeafStatus, Long> counts,
                             List<ChequeLeaf> skipped) {
        public long count(String status) {
            return counts.getOrDefault(LeafStatus.valueOf(status), 0L);
        }
    }

    /** Stock of one bank account (active books only). */
    public record AccountStock(CompanyBankAccount account, long activeBooks, long unused, long allocated, long used,
                               long voided, boolean lowStock, int lowStockLevel, long skipped) {
    }
}
