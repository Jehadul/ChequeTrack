package com.chequetrack.setup.service;

import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.event.Changes;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.common.exception.NotFoundException;
import com.chequetrack.security.service.CurrentUser;
import com.chequetrack.setup.SetupPermissions;
import com.chequetrack.setup.domain.Bank;
import com.chequetrack.setup.domain.BankBranch;
import com.chequetrack.setup.dto.BankBranchForm;
import com.chequetrack.setup.dto.BankForm;
import com.chequetrack.setup.repository.BankBranchRepository;
import com.chequetrack.setup.repository.BankRepository;
import com.chequetrack.setup.repository.CompanyBankAccountRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Banks and their branches (routing numbers). Company-wide list. */
@Service
public class BankService {

    private static final String VIEW = "hasAuthority('" + SetupPermissions.BANK_VIEW + "')";
    private static final String MANAGE = "hasAuthority('" + SetupPermissions.BANK_MANAGE + "')";

    private final BankRepository bankRepository;
    private final BankBranchRepository branchRepository;
    private final CompanyBankAccountRepository accountRepository;
    private final ApplicationEventPublisher events;

    public BankService(BankRepository bankRepository,
                       BankBranchRepository branchRepository,
                       CompanyBankAccountRepository accountRepository,
                       ApplicationEventPublisher events) {
        this.bankRepository = bankRepository;
        this.branchRepository = branchRepository;
        this.accountRepository = accountRepository;
        this.events = events;
    }

    // ---- banks ----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public List<Bank> list() {
        return bankRepository.findByCompanyIdOrderByName(companyId());
    }

    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public Map<Long, Long> branchCounts() {
        return branchRepository.branchCounts(companyId());
    }

    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public Bank get(Long id) {
        return bankRepository.findByIdAndCompanyId(id, companyId()).orElseThrow(() -> new NotFoundException("Bank", id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(MANAGE)
    public BankForm editForm(Long id) {
        Bank bank = get(id);
        BankForm form = new BankForm();
        form.setId(bank.getId());
        form.setVersion(bank.getVersion());
        form.setCode(bank.getCode());
        form.setName(bank.getName());
        form.setSwiftCode(bank.getSwiftCode());
        return form;
    }

    @Transactional
    @PreAuthorize(MANAGE)
    public Bank create(BankForm form) {
        if (bankRepository.existsByCompanyIdAndCodeIgnoreCase(companyId(), form.getCode().trim())) {
            throw new BusinessException("Bank code '" + form.getCode().trim().toUpperCase() + "' already exists.");
        }
        Bank bank = Bank.create(companyId(), form.getCode());
        bank.update(form.getName(), form.getSwiftCode());
        bankRepository.save(bank);
        events.publishEvent(AuditEvent.of("BANK_CREATED", "Bank", bank.getId(), "Created bank " + bank.getName()));
        return bank;
    }

    @Transactional
    @PreAuthorize(MANAGE)
    public void update(Long id, BankForm form) {
        Bank bank = get(id);
        bank.checkVersion(form.getVersion());
        Changes changes = new Changes()
                .add("name", bank.getName(), form.getName())
                .add("swiftCode", bank.getSwiftCode(), form.getSwiftCode() == null ? null : form.getSwiftCode().toUpperCase());
        bank.update(form.getName(), form.getSwiftCode());
        if (!changes.isEmpty()) {
            events.publishEvent(AuditEvent.of("BANK_UPDATED", "Bank", bank.getId(), "Updated bank " + bank.getCode(), changes));
        }
    }

    @Transactional
    @PreAuthorize(MANAGE)
    public void setBankActive(Long id, boolean active) {
        Bank bank = get(id);
        if (bank.isActive() != active) {
            bank.setActive(active);
            events.publishEvent(AuditEvent.of(active ? "BANK_ACTIVATED" : "BANK_DEACTIVATED", "Bank", bank.getId(),
                    (active ? "Activated" : "Deactivated") + " bank " + bank.getName()));
        }
    }

    // ---- bank branches --------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public List<BankBranch> branchesOf(Long bankId) {
        get(bankId);
        return branchRepository.findByBankIdOrderByName(bankId);
    }

    /** Active branches of active banks, for drop-downs (company accounts, received cheques). */
    @Transactional(readOnly = true)
    public List<BankBranch> selectableBranches() {
        return branchRepository.findSelectable(companyId());
    }

    @Transactional(readOnly = true)
    @PreAuthorize(MANAGE)
    public BankBranchForm branchEditForm(Long branchId) {
        BankBranch branch = getBranch(branchId);
        BankBranchForm form = new BankBranchForm();
        form.setId(branch.getId());
        form.setVersion(branch.getVersion());
        form.setName(branch.getName());
        form.setRoutingNumber(branch.getRoutingNumber());
        form.setDistrict(branch.getDistrict());
        form.setAddress(branch.getAddress());
        return form;
    }

    @Transactional
    @PreAuthorize(MANAGE)
    public BankBranch addBranch(Long bankId, BankBranchForm form) {
        Bank bank = get(bankId);
        checkRoutingFree(form.getRoutingNumber(), null);
        BankBranch branch = BankBranch.create(bank);
        branch.update(form.getName(), form.getRoutingNumber(), form.getDistrict(), form.getAddress());
        branchRepository.save(branch);
        events.publishEvent(AuditEvent.of("BANK_BRANCH_CREATED", "BankBranch", branch.getId(),
                "Added branch " + branch.getLabel()));
        return branch;
    }

    @Transactional
    @PreAuthorize(MANAGE)
    public void updateBranch(Long branchId, BankBranchForm form) {
        BankBranch branch = getBranch(branchId);
        branch.checkVersion(form.getVersion());
        checkRoutingFree(form.getRoutingNumber(), branch.getId());
        Changes changes = new Changes()
                .add("name", branch.getName(), form.getName())
                .add("routingNumber", branch.getRoutingNumber(), form.getRoutingNumber())
                .add("district", branch.getDistrict(), form.getDistrict())
                .add("address", branch.getAddress(), form.getAddress());
        branch.update(form.getName(), form.getRoutingNumber(), form.getDistrict(), form.getAddress());
        if (!changes.isEmpty()) {
            events.publishEvent(AuditEvent.of("BANK_BRANCH_UPDATED", "BankBranch", branch.getId(),
                    "Updated branch " + branch.getLabel(), changes));
        }
    }

    @Transactional
    @PreAuthorize(MANAGE)
    public Long setBranchActive(Long branchId, boolean active) {
        BankBranch branch = getBranch(branchId);
        if (!active && accountRepository.countUsingBankBranch(branchId) > 0) {
            throw new BusinessException("Company bank accounts are held at this branch. Deactivate them first.");
        }
        if (branch.isActive() != active) {
            branch.setActive(active);
            events.publishEvent(AuditEvent.of(active ? "BANK_BRANCH_ACTIVATED" : "BANK_BRANCH_DEACTIVATED",
                    "BankBranch", branch.getId(), (active ? "Activated " : "Deactivated ") + branch.getLabel()));
        }
        return branch.getBank().getId();
    }

    @Transactional(readOnly = true)
    public BankBranch getBranch(Long branchId) {
        return branchRepository.findByIdAndCompanyId(branchId, companyId())
                .orElseThrow(() -> new NotFoundException("Bank branch", branchId));
    }

    private void checkRoutingFree(String routingNumber, Long exceptId) {
        branchRepository.findByCompanyIdAndRoutingNumber(companyId(), routingNumber.trim())
                .filter(existing -> !existing.getId().equals(exceptId))
                .ifPresent(existing -> {
                    throw new BusinessException("Routing number " + routingNumber + " is already used by "
                            + existing.getLabel() + ".");
                });
    }

    private static Long companyId() {
        return CurrentUser.get().getCompanyId();
    }
}
