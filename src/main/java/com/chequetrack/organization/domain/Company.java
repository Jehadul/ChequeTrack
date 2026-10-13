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
@Table(name = "company")
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company extends BaseEntity {

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

    public void updateProfile(String name, String address, String phone, String email) {
        this.name = name.trim();
        this.address = blankToNull(address);
        this.phone = blankToNull(phone);
        this.email = blankToNull(email);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
