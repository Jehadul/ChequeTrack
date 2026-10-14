package com.chequetrack.setup.service;

import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.event.Changes;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.common.exception.NotFoundException;
import com.chequetrack.security.service.CurrentUser;
import com.chequetrack.setup.SetupPermissions;
import com.chequetrack.setup.domain.Party;
import com.chequetrack.setup.domain.PartyType;
import com.chequetrack.setup.dto.PartyForm;
import com.chequetrack.setup.repository.PartyRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Customers and suppliers (company-wide). */
@Service
public class PartyService {

    public static final int PAGE_SIZE = 25;
    private static final String VIEW = "hasAuthority('" + SetupPermissions.PARTY_VIEW + "')";
    private static final String MANAGE = "hasAuthority('" + SetupPermissions.PARTY_MANAGE + "')";

    private final PartyRepository partyRepository;
    private final ApplicationEventPublisher events;

    public PartyService(PartyRepository partyRepository, ApplicationEventPublisher events) {
        this.partyRepository = partyRepository;
        this.events = events;
    }

    /**
     * @param role CUSTOMER lists customers (and "both"), SUPPLIER lists suppliers (and "both"), null lists all
     */
    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public Page<Party> search(PartyType role, String query, int page) {
        String q = query == null || query.isBlank() ? "" : "%" + escapeLike(query.trim().toLowerCase()) + "%";
        return partyRepository.search(companyId(), typesFor(role), q,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by("name")));
    }

    /** Active customers (role CUSTOMER) or suppliers (role SUPPLIER), for drop-downs. */
    @Transactional(readOnly = true)
    public List<Party> selectable(PartyType role) {
        return partyRepository.findSelectable(companyId(), typesFor(role));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public Party get(Long id) {
        return partyRepository.findByIdAndCompanyId(id, companyId()).orElseThrow(() -> new NotFoundException("Party", id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(MANAGE)
    public PartyForm editForm(Long id) {
        Party p = get(id);
        PartyForm form = new PartyForm();
        form.setId(p.getId());
        form.setVersion(p.getVersion());
        form.setPartyType(p.getPartyType());
        form.setCode(p.getCode());
        form.setName(p.getName());
        form.setContactPerson(p.getContactPerson());
        form.setPhone(p.getPhone());
        form.setEmail(p.getEmail());
        form.setAddress(p.getAddress());
        form.setTin(p.getTin());
        return form;
    }

    @Transactional
    @PreAuthorize(MANAGE)
    public Party create(PartyForm form) {
        if (partyRepository.existsByCompanyIdAndCodeIgnoreCase(companyId(), form.getCode().trim())) {
            throw new BusinessException("Code '" + form.getCode().trim().toUpperCase() + "' is already used.");
        }
        Party party = Party.create(companyId(), form.getCode());
        apply(party, form);
        partyRepository.save(party);
        events.publishEvent(AuditEvent.of("PARTY_CREATED", "Party", party.getId(),
                "Created " + party.getPartyType().getLabel().toLowerCase() + " " + party.getLabel()));
        return party;
    }

    @Transactional
    @PreAuthorize(MANAGE)
    public void update(Long id, PartyForm form) {
        Party party = get(id);
        party.checkVersion(form.getVersion());
        Changes changes = new Changes()
                .add("type", party.getPartyType(), form.getPartyType())
                .add("name", party.getName(), form.getName())
                .add("contactPerson", party.getContactPerson(), form.getContactPerson())
                .add("phone", party.getPhone(), form.getPhone())
                .add("email", party.getEmail(), form.getEmail() == null ? null : form.getEmail().toLowerCase())
                .add("address", party.getAddress(), form.getAddress())
                .add("tin", party.getTin(), form.getTin());
        apply(party, form);
        if (!changes.isEmpty()) {
            events.publishEvent(AuditEvent.of("PARTY_UPDATED", "Party", party.getId(), "Updated " + party.getLabel(), changes));
        }
    }

    @Transactional
    @PreAuthorize(MANAGE)
    public void setActive(Long id, boolean active) {
        Party party = get(id);
        if (party.isActive() != active) {
            party.setActive(active);
            events.publishEvent(AuditEvent.of(active ? "PARTY_ACTIVATED" : "PARTY_DEACTIVATED", "Party", party.getId(),
                    (active ? "Activated " : "Deactivated ") + party.getLabel()));
        }
    }

    private static void apply(Party party, PartyForm form) {
        party.update(form.getPartyType(), form.getName(), form.getContactPerson(), form.getPhone(), form.getEmail(),
                form.getAddress(), form.getTin());
    }

    private static Set<PartyType> typesFor(PartyType role) {
        if (role == PartyType.CUSTOMER) {
            return EnumSet.of(PartyType.CUSTOMER, PartyType.BOTH);
        }
        if (role == PartyType.SUPPLIER) {
            return EnumSet.of(PartyType.SUPPLIER, PartyType.BOTH);
        }
        return EnumSet.allOf(PartyType.class);
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Long companyId() {
        return CurrentUser.get().getCompanyId();
    }
}
