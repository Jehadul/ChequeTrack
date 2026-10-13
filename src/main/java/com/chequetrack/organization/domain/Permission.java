package com.chequetrack.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** One entry of the permission catalogue. Rows are added by migrations only. */
@Getter
@Entity
@Immutable
@Table(name = "permission")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Permission {

    @Id
    @Column(length = 50)
    private String code;

    @Column(name = "group_name", nullable = false, length = 50)
    private String groupName;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
