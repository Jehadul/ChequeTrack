package com.chequetrack.security.service;

import java.util.Set;

public record UserAccess(String roleCode, String roleName, Set<String> permissions) {

    public static final UserAccess NONE = new UserAccess("NONE", "No role", Set.of());
}
