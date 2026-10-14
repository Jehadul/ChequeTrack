package com.chequetrack.setup.service;

import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.event.Changes;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.common.exception.NotFoundException;
import com.chequetrack.security.service.CurrentUser;
import com.chequetrack.setup.SetupPermissions;
import com.chequetrack.setup.domain.Reason;
import com.chequetrack.setup.domain.ReasonType;
import com.chequetrack.setup.dto.ReasonForm;
import com.chequetrack.setup.repository.ReasonRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Reason lists (bounce, void, defer, ...). Reasons are deactivated, never deleted, because history refers to them. */
@Service
public class ReasonService {

    private static final String MANAGE = "hasAuthority('" + SetupPermissions.SETTINGS_MANAGE + "')";

    private final ReasonRepository reasonRepository;
    private final ApplicationEventPublisher events;

    public ReasonService(ReasonRepository reasonRepository, ApplicationEventPublisher events) {
        this.reasonRepository = reasonRepository;
        this.events = events;
    }

    /** All reasons grouped by list, in display order. */
    @Transactional(readOnly = true)
    @PreAuthorize(MANAGE)
    public Map<ReasonType, List<Reason>> allGrouped() {
        Map<ReasonType, List<Reason>> groups = new EnumMap<>(ReasonType.class);
        for (ReasonType type : ReasonType.values()) {
            groups.put(type, new ArrayList<>());
        }
        reasonRepository.findByCompanyIdOrderByReasonTypeAscSortOrderAscDescriptionAsc(companyId())
                .forEach(r -> groups.get(r.getReasonType()).add(r));
        return groups;
    }

    /** Active reasons of one list, for drop-downs (bounce, void, ...). */
    @Transactional(readOnly = true)
    public List<Reason> active(ReasonType type) {
        return reasonRepository.findByCompanyIdAndReasonTypeAndActiveTrueOrderBySortOrderAscDescriptionAsc(companyId(), type);
    }

    @Transactional(readOnly = true)
    public Reason get(Long id) {
        return reasonRepository.findByIdAndCompanyId(id, companyId()).orElseThrow(() -> new NotFoundException("Reason", id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(MANAGE)
    public ReasonForm editForm(Long id) {
        Reason r = get(id);
        ReasonForm form = new ReasonForm();
        form.setId(r.getId());
        form.setVersion(r.getVersion());
        form.setReasonType(r.getReasonType());
        form.setCode(r.getCode());
        form.setDescription(r.getDescription());
        form.setSortOrder(r.getSortOrder());
        return form;
    }

    @Transactional
    @PreAuthorize(MANAGE)
    public Reason create(ReasonForm form) {
        if (reasonRepository.existsByCompanyIdAndReasonTypeAndCodeIgnoreCase(companyId(), form.getReasonType(), form.getCode().trim())) {
            throw new BusinessException("Code '" + form.getCode().trim().toUpperCase() + "' already exists in the "
                    + form.getReasonType().getLabel().toLowerCase() + " list.");
        }
        Reason reason = Reason.create(companyId(), form.getReasonType(), form.getCode());
        reason.update(form.getDescription(), form.getSortOrder());
        reasonRepository.save(reason);
        events.publishEvent(AuditEvent.of("REASON_CREATED", "Reason", reason.getId(),
                "Added " + reason.getReasonType().getLabel().toLowerCase() + " reason: " + reason.getDescription()));
        return reason;
    }

    /** List and code are fixed; description and order can change. */
    @Transactional
    @PreAuthorize(MANAGE)
    public void update(Long id, ReasonForm form) {
        Reason reason = get(id);
        reason.checkVersion(form.getVersion());
        Changes changes = new Changes()
                .add("description", reason.getDescription(), form.getDescription())
                .add("sortOrder", reason.getSortOrder(), form.getSortOrder());
        reason.update(form.getDescription(), form.getSortOrder());
        if (!changes.isEmpty()) {
            events.publishEvent(AuditEvent.of("REASON_UPDATED", "Reason", reason.getId(),
                    "Updated " + reason.getReasonType().getLabel().toLowerCase() + " reason " + reason.getCode(), changes));
        }
    }

    @Transactional
    @PreAuthorize(MANAGE)
    public void setActive(Long id, boolean active) {
        Reason reason = get(id);
        if (reason.isActive() != active) {
            reason.setActive(active);
            events.publishEvent(AuditEvent.of(active ? "REASON_ACTIVATED" : "REASON_DEACTIVATED", "Reason", reason.getId(),
                    (active ? "Activated " : "Deactivated ") + reason.getReasonType().getLabel().toLowerCase()
                            + " reason: " + reason.getDescription()));
        }
    }

    private static Long companyId() {
        return CurrentUser.get().getCompanyId();
    }
}
