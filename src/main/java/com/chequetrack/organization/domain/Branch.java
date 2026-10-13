package com.chequetrack.organization.domain;

import com.chequetrack.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Entity
@Table(name = "branch")
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Branch extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 300)
    private String address;

    @Column(length = 50)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(nullable = false)
    private boolean active = true;

    public static Branch create(Long companyId, String code) {
        Branch branch = new Branch();
        branch.companyId = companyId;
        branch.code = code.trim().toUpperCase();
        return branch;
    }

    public void update(String name, String address, String phone, String email) {
        this.name = name.trim();
        this.address = Company.blankToNull(address);
        this.phone = Company.blankToNull(phone);
        this.email = Company.blankToNull(email);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /** "HO - Head Office" */
    public String getLabel() {
        return code + " - " + name;
    }
}
