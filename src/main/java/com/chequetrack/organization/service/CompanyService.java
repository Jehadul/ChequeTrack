package com.chequetrack.organization.service;

import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.event.Changes;
import com.chequetrack.common.exception.NotFoundException;
import com.chequetrack.organization.OrganizationPermissions;
import com.chequetrack.organization.domain.Company;
import com.chequetrack.organization.dto.CompanyForm;
import com.chequetrack.organization.repository.CompanyRepository;
import com.chequetrack.security.service.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The logged-in user's own company. */
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Company current() {
        Long companyId = CurrentUser.get().getCompanyId();
        return companyRepository.findById(companyId).orElseThrow(() -> new NotFoundException("Company", companyId));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('" + OrganizationPermissions.COMPANY_MANAGE + "')")
    public CompanyForm editForm() {
        Company company = current();
        CompanyForm form = new CompanyForm();
        form.setVersion(company.getVersion());
        form.setName(company.getName());
        form.setAddress(company.getAddress());
        form.setPhone(company.getPhone());
        form.setEmail(company.getEmail());
        return form;
    }

    @Transactional
    @PreAuthorize("hasAuthority('" + OrganizationPermissions.COMPANY_MANAGE + "')")
    public void update(CompanyForm form) {
        Company company = current();
        company.checkVersion(form.getVersion());
        Changes changes = new Changes()
                .add("name", company.getName(), form.getName())
                .add("address", company.getAddress(), form.getAddress())
                .add("phone", company.getPhone(), form.getPhone())
                .add("email", company.getEmail(), form.getEmail());
        company.updateProfile(form.getName(), form.getAddress(), form.getPhone(), form.getEmail());
        if (!changes.isEmpty()) {
            events.publishEvent(AuditEvent.of("COMPANY_UPDATED", "Company", company.getId(),
                    "Updated the company profile", changes));
        }
    }
}
