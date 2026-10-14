package com.chequetrack.setup.service;

import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.event.Changes;
import com.chequetrack.security.service.CurrentUser;
import com.chequetrack.setup.SetupPermissions;
import com.chequetrack.setup.domain.CompanySettings;
import com.chequetrack.setup.dto.SettingsForm;
import com.chequetrack.setup.repository.CompanySettingsRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cheque rules of the user's company. Read by later modules (validity, reminders, stock alerts). */
@Service
public class SettingsService {

    private final CompanySettingsRepository settingsRepository;
    private final ApplicationEventPublisher events;

    public SettingsService(CompanySettingsRepository settingsRepository, ApplicationEventPublisher events) {
        this.settingsRepository = settingsRepository;
        this.events = events;
    }

    /** Settings of the logged-in user's company. */
    @Transactional(readOnly = true)
    public CompanySettings current() {
        return forCompany(CurrentUser.get().getCompanyId());
    }

    /** Settings of a given company (for scheduled jobs that run without a user). */
    @Transactional(readOnly = true)
    public CompanySettings forCompany(Long companyId) {
        return settingsRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new IllegalStateException("No settings row for company " + companyId));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + SetupPermissions.SETTINGS_MANAGE + "')")
    public SettingsForm editForm() {
        CompanySettings s = current();
        SettingsForm form = new SettingsForm();
        form.setVersion(s.getVersion());
        form.setChequeValidityDays(s.getChequeValidityDays());
        form.setNearExpiryDays(s.getNearExpiryDays());
        form.setReminderLeadDays(s.getReminderLeadDays());
        form.setMaxDefers(s.getMaxDefers());
        form.setLowStockLeaves(s.getLowStockLeaves());
        form.setBounceNoticeDays(s.getBounceNoticeDays());
        return form;
    }

    @Transactional
    @PreAuthorize("hasAuthority('" + SetupPermissions.SETTINGS_MANAGE + "')")
    public void update(SettingsForm form) {
        CompanySettings s = current();
        s.checkVersion(form.getVersion());
        Changes changes = new Changes()
                .add("chequeValidityDays", s.getChequeValidityDays(), form.getChequeValidityDays())
                .add("nearExpiryDays", s.getNearExpiryDays(), form.getNearExpiryDays())
                .add("reminderLeadDays", s.getReminderLeadDays(), form.getReminderLeadDays())
                .add("maxDefers", s.getMaxDefers(), form.getMaxDefers())
                .add("lowStockLeaves", s.getLowStockLeaves(), form.getLowStockLeaves())
                .add("bounceNoticeDays", s.getBounceNoticeDays(), form.getBounceNoticeDays());
        s.update(form.getChequeValidityDays(), form.getNearExpiryDays(), form.getReminderLeadDays(),
                form.getMaxDefers(), form.getLowStockLeaves(), form.getBounceNoticeDays());
        if (!changes.isEmpty()) {
            events.publishEvent(AuditEvent.of("SETTINGS_UPDATED", "CompanySettings", s.getId(),
                    "Updated cheque settings", changes));
        }
    }
}
