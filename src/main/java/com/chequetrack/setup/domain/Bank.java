package com.chequetrack.setup.domain;

import com.chequetrack.common.domain.BaseEntity;
import com.chequetrack.common.domain.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/** A bank, e.g. "Dutch-Bangla Bank PLC". Company-wide list. */
@Getter
@Entity
@Table(name = "bank")
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bank extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "swift_code", length = 11)
    private String swiftCode;

    @Column(nullable = false)
    private boolean active = true;

    public static Bank create(Long companyId, String code) {
        Bank bank = new Bank();
        bank.companyId = companyId;
        bank.code = Text.code(code);
        return bank;
    }

    public void update(String name, String swiftCode) {
        this.name = name.trim();
        this.swiftCode = Text.code(swiftCode);
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
