package com.chequetrack.setup.domain;

import com.chequetrack.common.domain.Text;
import com.chequetrack.organization.scope.BranchScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * A bank account the company issues cheques from. Operated by one branch, so it
 * is branch-scoped: users only see accounts of their branches.
 */
@Getter
@Entity
@Table(name = "company_bank_account")
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanyBankAccount extends BranchScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_branch_id", nullable = false)
    private BankBranch bankBranch;

    @Column(name = "account_name", nullable = false, length = 150)
    private String accountName;

    @Column(name = "account_number", nullable = false, length = 30)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Column(name = "gl_code", length = 30)
    private String glCode;

    @Column(nullable = false)
    private boolean active = true;

    public static CompanyBankAccount create(Long companyId, Long branchId) {
        CompanyBankAccount account = new CompanyBankAccount();
        account.assignScope(companyId, branchId);
        return account;
    }

    public void update(Long branchId, BankBranch bankBranch, String accountName, String accountNumber,
                       AccountType accountType, String glCode) {
        moveToBranch(branchId);
        this.bankBranch = bankBranch;
        this.accountName = accountName.trim();
        this.accountNumber = accountNumber.trim();
        this.accountType = accountType;
        this.glCode = Text.clean(glCode);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /** "DBBL 1234567890 (Current)" */
    public String getLabel() {
        return bankBranch.getBank().getCode() + " " + accountNumber + " (" + accountType.getLabel() + ")";
    }
}
