package com.chequetrack.setup.service;

import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.event.Changes;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.organization.repository.BranchRepository;
import com.chequetrack.organization.scope.BranchScope;
import com.chequetrack.security.service.CurrentUser;
import com.chequetrack.setup.SetupPermissions;
import com.chequetrack.setup.domain.BankBranch;
import com.chequetrack.setup.domain.CompanyBankAccount;
import com.chequetrack.setup.dto.AccountForm;
import com.chequetrack.setup.repository.CompanyBankAccountRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Company bank accounts. Branch-scoped: lists come through the branch filter,
 * single records through {@link BranchScope#require}.
 */
@Service
public class CompanyAccountService {

    private static final String VIEW = "hasAuthority('" + SetupPermissions.BANK_VIEW + "')";
    private static final String MANAGE = "hasAuthority('" + SetupPermissions.BANK_MANAGE + "')";

    private final CompanyBankAccountRepository accountRepository;
    private final BankService bankService;
    private final BranchScope branchScope;
    private final BranchRepository branchRepository;
    private final ApplicationEventPublisher events;

    public CompanyAccountService(CompanyBankAccountRepository accountRepository,
                                 BankService bankService,
                                 BranchScope branchScope,
                                 BranchRepository branchRepository,
                                 ApplicationEventPublisher events) {
        this.accountRepository = accountRepository;
        this.bankService = bankService;
        this.branchScope = branchScope;
        this.branchRepository = branchRepository;
        this.events = events;
    }

    /** Accounts of the branches the user may see (enforced by the branch filter). */
    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public List<CompanyBankAccount> listVisible() {
        return accountRepository.findAllVisible();
    }

    /** Active accounts the user may see, for drop-downs (cheque books in M6). */
    @Transactional(readOnly = true)
    public List<CompanyBankAccount> selectable() {
        return accountRepository.findAllVisible().stream().filter(CompanyBankAccount::isActive).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize(VIEW)
    public CompanyBankAccount get(Long id) {
        return branchScope.require(accountRepository.findById(id), "Company bank account", id);
    }

    /** Branch codes by id, for showing which branch operates each account. */
    @Transactional(readOnly = true)
    public Map<Long, String> branchCodes() {
        return branchRepository.findByCompanyIdOrderByCode(CurrentUser.get().getCompanyId()).stream()
                .collect(Collectors.toMap(b -> b.getId(), b -> b.getCode()));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(MANAGE)
    public AccountForm editForm(Long id) {
        CompanyBankAccount a = get(id);
        AccountForm form = new AccountForm();
        form.setId(a.getId());
        form.setVersion(a.getVersion());
        form.setBranchId(a.getBranchId());
        form.setBankBranchId(a.getBankBranch().getId());
        form.setAccountName(a.getAccountName());
        form.setAccountNumber(a.getAccountNumber());
        form.setAccountType(a.getAccountType());
        form.setGlCode(a.getGlCode());
        return form;
    }

    @Transactional
    @PreAuthorize(MANAGE)
    public CompanyBankAccount create(AccountForm form) {
        Long companyId = CurrentUser.get().getCompanyId();
        Long branchId = branchScope.branchForNewRecord(form.getBranchId());
        BankBranch bankBranch = activeBankBranch(form.getBankBranchId());
        checkNumberFree(companyId, bankBranch.getId(), form.getAccountNumber(), null);

        CompanyBankAccount account = CompanyBankAccount.create(companyId, branchId);
        account.update(branchId, bankBranch, form.getAccountName(), form.getAccountNumber(), form.getAccountType(),
                form.getGlCode());
        accountRepository.save(account);
        events.publishEvent(AuditEvent.of("ACCOUNT_CREATED", "CompanyBankAccount", account.getId(),
                "Created company bank account " + account.getLabel() + " at " + bankBranch.getLabel()));
        return account;
    }

    @Transactional
    @PreAuthorize(MANAGE)
    public void update(Long id, AccountForm form) {
        CompanyBankAccount account = get(id);
        account.checkVersion(form.getVersion());
        Long branchId = branchScope.branchForNewRecord(form.getBranchId());
        BankBranch bankBranch = form.getBankBranchId().equals(account.getBankBranch().getId())
                ? account.getBankBranch() : activeBankBranch(form.getBankBranchId());
        checkNumberFree(account.getCompanyId(), bankBranch.getId(), form.getAccountNumber(), account.getId());

        Map<Long, String> codes = branchCodes();
        Changes changes = new Changes()
                .add("branch", codes.get(account.getBranchId()), codes.get(branchId))
                .add("bankBranch", account.getBankBranch().getLabel(), bankBranch.getLabel())
                .add("accountName", account.getAccountName(), form.getAccountName())
                .add("accountNumber", account.getAccountNumber(), form.getAccountNumber())
                .add("accountType", account.getAccountType(), form.getAccountType())
                .add("glCode", account.getGlCode(), form.getGlCode());
        account.update(branchId, bankBranch, form.getAccountName(), form.getAccountNumber(), form.getAccountType(),
                form.getGlCode());
        if (!changes.isEmpty()) {
            events.publishEvent(AuditEvent.of("ACCOUNT_UPDATED", "CompanyBankAccount", account.getId(),
                    "Updated company bank account " + account.getLabel(), changes));
        }
    }

    @Transactional
    @PreAuthorize(MANAGE)
    public void setActive(Long id, boolean active) {
        CompanyBankAccount account = get(id);
        if (account.isActive() != active) {
            account.setActive(active);
            events.publishEvent(AuditEvent.of(active ? "ACCOUNT_ACTIVATED" : "ACCOUNT_DEACTIVATED",
                    "CompanyBankAccount", account.getId(),
                    (active ? "Activated" : "Deactivated") + " company bank account " + account.getLabel()));
        }
    }

    private BankBranch activeBankBranch(Long bankBranchId) {
        BankBranch bankBranch = bankService.getBranch(bankBranchId);
        if (!bankBranch.isActive() || !bankBranch.getBank().isActive()) {
            throw new BusinessException("Bank branch " + bankBranch.getLabel() + " is inactive.");
        }
        return bankBranch;
    }

    private void checkNumberFree(Long companyId, Long bankBranchId, String number, Long exceptId) {
        if (accountRepository.numberTaken(companyId, bankBranchId, number.trim(), exceptId)) {
            throw new BusinessException("Account number " + number.trim() + " already exists at this bank branch.");
        }
    }
}
