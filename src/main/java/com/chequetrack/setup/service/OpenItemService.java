package com.chequetrack.setup.service;

import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.event.Changes;
import com.chequetrack.common.event.StatusChangedEvent;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.organization.scope.BranchScope;
import com.chequetrack.security.service.CurrentUser;
import com.chequetrack.setup.SetupPermissions;
import com.chequetrack.setup.domain.OpenItem;
import com.chequetrack.setup.domain.OpenItemStatus;
import com.chequetrack.setup.domain.OpenItemType;
import com.chequetrack.setup.domain.Party;
import com.chequetrack.setup.dto.OpenItemForm;
import com.chequetrack.setup.repository.OpenItemRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

/**
 * Customer invoices and supplier bills. Branch-scoped: users only see items of
 * their branches. Cancelling needs a reason, kept in the status history.
 */
@Service
public class OpenItemService {

    public static final int PAGE_SIZE = 25;
    public static final String ENTITY = "OpenItem";
    private static final String VIEW = "hasAuthority('" + SetupPermissions.INVOICE_VIEW + "')";
    private static final String MANAGE = "hasAuthority('" + SetupPermissions.INVOICE_MANAGE + "')";

    private final OpenItemRepository itemRepository;
    private final PartyService partyService;
    private final BranchScope branchScope;
    private final ApplicationEventPublisher events;

    public OpenItemService(OpenItemRepository itemRepository,
                           PartyService partyService,
                           BranchScope branchScope,
                           ApplicationEventPublisher events) {
        this.itemRepository = itemRepository;
        this.partyService = partyService;
        this.branchScope = branchScope;
        this.events = events;
    }

    /** @param openOnly true = only items still to be settled */
    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public Page<OpenItem> search(OpenItemType type, boolean openOnly, String query, int page) {
        Set<OpenItemStatus> statuses = openOnly
                ? EnumSet.of(OpenItemStatus.OPEN, OpenItemStatus.PARTLY_SETTLED)
                : EnumSet.allOf(OpenItemStatus.class);
        String q = query == null || query.isBlank() ? "" : "%" + PartyService.escapeLike(query.trim().toLowerCase()) + "%";
        return itemRepository.search(type, statuses, q,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Order.desc("itemDate"), Sort.Order.desc("id"))));
    }

    /** Open invoices (or bills) of one customer (or supplier) the user may see, oldest first. */
    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public java.util.List<OpenItem> openForParty(Long partyId, OpenItemType type) {
        return itemRepository.findOpenForParty(partyId, type);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public OpenItem get(Long id) {
        OpenItem item = branchScope.require(itemRepository.findById(id), "Invoice or bill", id);
        item.getParty().getName();  // load for the view (open-in-view is off)
        return item;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(MANAGE)
    public OpenItemForm editForm(Long id) {
        OpenItem item = get(id);
        OpenItemForm form = new OpenItemForm();
        form.setId(item.getId());
        form.setVersion(item.getVersion());
        form.setItemType(item.getItemType());
        form.setBranchId(item.getBranchId());
        form.setPartyId(item.getParty().getId());
        form.setNumber(item.getNumber());
        form.setItemDate(item.getItemDate());
        form.setDueDate(item.getDueDate());
        form.setAmount(item.getAmount());
        form.setNarration(item.getNarration());
        return form;
    }

    @Transactional
    @PreAuthorize(MANAGE)
    public OpenItem create(OpenItemForm form) {
        Long companyId = CurrentUser.get().getCompanyId();
        Long branchId = branchScope.branchForNewRecord(form.getBranchId());
        Party party = validParty(form);
        checkNumberFree(companyId, form, null);

        OpenItem item = OpenItem.create(companyId, branchId, form.getItemType());
        item.update(party, form.getNumber(), form.getItemDate(), form.getDueDate(), form.getAmount(), form.getNarration());
        itemRepository.save(item);
        events.publishEvent(AuditEvent.of(form.getItemType() + "_CREATED", ENTITY, item.getId(),
                "Created " + label(item) + " for " + party.getName() + ", amount " + item.getAmount().toPlainString()));
        events.publishEvent(new StatusChangedEvent(ENTITY, item.getId(), companyId, branchId, null,
                OpenItemStatus.OPEN.name(), "Created"));
        return item;
    }

    /** Only open items can be edited; the branch stays the same. */
    @Transactional
    @PreAuthorize(MANAGE)
    public void update(Long id, OpenItemForm form) {
        OpenItem item = get(id);
        item.checkVersion(form.getVersion());
        form.setItemType(item.getItemType());
        Party party = validParty(form);
        checkNumberFree(item.getCompanyId(), form, item.getId());
        Changes changes = new Changes()
                .add("party", item.getParty().getLabel(), party.getLabel())
                .add("number", item.getNumber(), form.getNumber().toUpperCase())
                .add("date", item.getItemDate(), form.getItemDate())
                .add("dueDate", item.getDueDate(), form.getDueDate())
                .add("amount", item.getAmount().stripTrailingZeros().toPlainString(),
                        form.getAmount().stripTrailingZeros().toPlainString())
                .add("narration", item.getNarration(), form.getNarration());
        item.update(party, form.getNumber(), form.getItemDate(), form.getDueDate(), form.getAmount(), form.getNarration());
        if (!changes.isEmpty()) {
            events.publishEvent(AuditEvent.of(item.getItemType() + "_UPDATED", ENTITY, item.getId(),
                    "Updated " + label(item), changes));
        }
    }

    @Transactional
    @PreAuthorize(MANAGE)
    public void cancel(Long id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Enter the reason for cancelling.");
        }
        if (reason.length() > 300) {
            throw new BusinessException("The reason must be at most 300 characters.");
        }
        OpenItem item = get(id);
        OpenItemStatus from = item.getStatus();
        item.cancel();
        events.publishEvent(new StatusChangedEvent(ENTITY, item.getId(), item.getCompanyId(), item.getBranchId(),
                from.name(), item.getStatus().name(), reason));
        events.publishEvent(AuditEvent.of(item.getItemType() + "_CANCELLED", ENTITY, item.getId(),
                "Cancelled " + label(item) + ": " + reason.trim()));
    }

    private Party validParty(OpenItemForm form) {
        Party party = partyService.get(form.getPartyId());
        if (!party.isActive()) {
            throw new BusinessException(party.getName() + " is inactive.");
        }
        if (!form.getItemType().allowedFor(party.getPartyType())) {
            throw new BusinessException(party.getName() + " is not a "
                    + (form.getItemType() == OpenItemType.INVOICE ? "customer" : "supplier") + ".");
        }
        return party;
    }

    private void checkNumberFree(Long companyId, OpenItemForm form, Long exceptId) {
        if (itemRepository.numberTaken(companyId, form.getItemType().name(), form.getNumber().trim(), exceptId)) {
            throw new BusinessException(form.getItemType().getLabel() + " number " + form.getNumber().trim().toUpperCase()
                    + " already exists.");
        }
    }

    private static String label(OpenItem item) {
        return item.getItemType().getLabel().toLowerCase() + " " + item.getNumber();
    }
}
