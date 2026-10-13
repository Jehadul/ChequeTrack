package com.chequetrack.security.service;

/**
 * Looks up a role's code, name and permissions. Implemented by the
 * organization module, which owns roles; defined here so this module does not
 * depend on it (ARCHITECTURE.md section 1, dependency rule).
 */
public interface UserAccessProvider {

    UserAccess forRole(Long roleId);
}
