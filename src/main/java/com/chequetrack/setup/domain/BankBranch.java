package com.chequetrack.setup.domain;

import com.chequetrack.common.domain.BaseEntity;
import com.chequetrack.common.domain.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/** A branch of a bank, identified by its 9-digit routing number. */
@Getter
@Entity
@Table(name = "bank_branch")
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankBranch extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_id", nullable = false, updatable = false)
    private Bank bank;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "routing_number", nullable = false, length = 9)
    private String routingNumber;

    @Column(length = 50)
    private String district;

    @Column(length = 300)
    private String address;

    @Column(nullable = false)
    private boolean active = true;

    public static BankBranch create(Bank bank) {
        BankBranch branch = new BankBranch();
        branch.companyId = bank.getCompanyId();
        branch.bank = bank;
        return branch;
    }

    public void update(String name, String routingNumber, String district, String address) {
        this.name = name.trim();
        this.routingNumber = routingNumber.trim();
        this.district = Text.clean(district);
        this.address = Text.clean(address);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /** "Dutch-Bangla Bank PLC, Gulshan (090261725)" */
    public String getLabel() {
        return bank.getName() + ", " + name + " (" + routingNumber + ")";
    }
}
