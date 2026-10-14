package com.chequetrack.setup.domain;

import com.chequetrack.common.domain.BaseEntity;
import com.chequetrack.common.domain.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/** A customer (pays us by cheque), a supplier (we pay by cheque), or both. Company-wide. */
@Getter
@Entity
@Table(name = "party")
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Party extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false, length = 10)
    private PartyType partyType;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "contact_person", length = 100)
    private String contactPerson;

    @Column(length = 50)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(length = 300)
    private String address;

    @Column(length = 20)
    private String tin;

    @Column(nullable = false)
    private boolean active = true;

    public static Party create(Long companyId, String code) {
        Party party = new Party();
        party.companyId = companyId;
        party.code = Text.code(code);
        return party;
    }

    public void update(PartyType type, String name, String contactPerson, String phone, String email,
                       String address, String tin) {
        this.partyType = type;
        this.name = name.trim();
        this.contactPerson = Text.clean(contactPerson);
        this.phone = Text.clean(phone);
        this.email = email == null || email.isBlank() ? null : email.trim().toLowerCase();
        this.address = Text.clean(address);
        this.tin = Text.clean(tin);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /** "C001 - Rahman Traders" */
    public String getLabel() {
        return code + " - " + name;
    }
}
