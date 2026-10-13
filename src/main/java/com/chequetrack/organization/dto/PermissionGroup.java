package com.chequetrack.organization.dto;

import com.chequetrack.organization.domain.Permission;

import java.util.List;

/** Permissions of one group, for the role edit screen. */
public record PermissionGroup(String name, List<Permission> permissions) {
}
