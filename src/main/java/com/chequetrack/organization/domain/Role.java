package com.chequetrack.organization.domain;

import com.chequetrack.common.domain.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** A named set of permissions. Built-in (system) roles cannot be renamed or deleted. */
@Getter
@Entity
@Table(name = "role")
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permission", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission_code")
    private Set<String> permissionCodes = new HashSet<>();

    public static Role create(Long companyId, String code) {
        Role role = new Role();
        role.companyId = companyId;
        role.code = code.trim().toUpperCase();
        return role;
    }

    public void update(String name, String description) {
        this.name = name.trim();
        this.description = Company.blankToNull(description);
    }

    /** @return true when the permission set actually changed */
    public boolean replacePermissions(Collection<String> codes) {
        Set<String> next = new HashSet<>(codes);
        if (next.equals(permissionCodes)) {
            return false;
        }
        permissionCodes.clear();
        permissionCodes.addAll(next);
        return true;
    }
}
