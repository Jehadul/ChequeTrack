package com.chequetrack.printing.service;

import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.event.Changes;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.common.exception.NotFoundException;
import com.chequetrack.printing.PrintingPermissions;
import com.chequetrack.printing.domain.PrinterProfile;
import com.chequetrack.printing.dto.PrinterForm;
import com.chequetrack.printing.repository.PrinterProfileRepository;
import com.chequetrack.security.service.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PrinterService {

    public static final String ENTITY = "PrinterProfile";
    private static final String MANAGE = "hasAuthority('" + PrintingPermissions.LAYOUT_MANAGE + "')";

    private final PrinterProfileRepository printerRepository;
    private final ApplicationEventPublisher events;

    public PrinterService(PrinterProfileRepository printerRepository, ApplicationEventPublisher events) {
        this.printerRepository = printerRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(MANAGE)
    public List<PrinterProfile> list() {
        return printerRepository.findByCompanyIdOrderByName(CurrentUser.get().getCompanyId());
    }

    /** Active printers, for choosing one when printing. */
    @Transactional(readOnly = true)
    public List<PrinterProfile> selectable() {
        return printerRepository.findByCompanyIdOrderByName(CurrentUser.get().getCompanyId()).stream()
                .filter(PrinterProfile::isActive).toList();
    }

    /** A printer of the user's company, or null when none was chosen. */
    @Transactional(readOnly = true)
    public PrinterProfile find(Long id) {
        if (id == null) {
            return null;
        }
        return printerRepository.findById(id)
                .filter(p -> p.getCompanyId().equals(CurrentUser.get().getCompanyId()) && p.isActive())
                .orElseThrow(() -> new NotFoundException("Printer", id));
    }

    @Transactional
    @PreAuthorize(MANAGE)
    public PrinterProfile save(PrinterForm form) {
        Long companyId = CurrentUser.get().getCompanyId();
        if (printerRepository.existsByCompanyIdAndNameIgnoreCaseAndIdNot(companyId, form.getName().trim(),
                form.getId() == null ? -1L : form.getId())) {
            throw new BusinessException("A printer named '" + form.getName().trim() + "' already exists.");
        }
        if (form.getId() == null) {
            PrinterProfile p = PrinterProfile.create(companyId);
            p.update(form.getName(), form.getOffsetXMm(), form.getOffsetYMm(), form.getNotes());
            printerRepository.save(p);
            events.publishEvent(AuditEvent.of("PRINTER_CREATED", ENTITY, p.getId(), "Added printer " + p.getLabel()));
            return p;
        }
        PrinterProfile p = mine(form.getId());
        p.checkVersion(form.getVersion());
        Changes changes = new Changes()
                .add("name", p.getName(), form.getName().trim())
                .add("offsetX", p.getOffsetXMm(), form.getOffsetXMm())
                .add("offsetY", p.getOffsetYMm(), form.getOffsetYMm())
                .add("notes", p.getNotes(), form.getNotes());
        p.update(form.getName(), form.getOffsetXMm(), form.getOffsetYMm(), form.getNotes());
        if (!changes.isEmpty()) {
            events.publishEvent(AuditEvent.of("PRINTER_UPDATED", ENTITY, p.getId(), "Updated printer " + p.getName(), changes));
        }
        return p;
    }

    @Transactional
    @PreAuthorize(MANAGE)
    public void setActive(Long id, boolean active) {
        PrinterProfile p = mine(id);
        if (p.isActive() != active) {
            p.setActive(active);
            events.publishEvent(AuditEvent.of(active ? "PRINTER_ACTIVATED" : "PRINTER_DEACTIVATED", ENTITY, p.getId(),
                    (active ? "Activated" : "Deactivated") + " printer " + p.getName()));
        }
    }

    private PrinterProfile mine(Long id) {
        return printerRepository.findById(id)
                .filter(p -> p.getCompanyId().equals(CurrentUser.get().getCompanyId()))
                .orElseThrow(() -> new NotFoundException("Printer", id));
    }
}
