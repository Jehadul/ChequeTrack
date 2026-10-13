package com.chequetrack.security.service;

import java.util.Set;

/**
 * What a user's role allows: the role itself and its permission codes.
 * Supplied by {@link UserAccessProvider}.
 */
public record UserAccess(String roleCode, String roleName, Set<String> permissions) {

    public static final UserAccess NONE = new UserAccess("NONE", "No role", Set.of());
}
