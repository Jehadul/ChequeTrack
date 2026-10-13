package com.chequetrack.organization;

/**
 * Permission codes owned by this module (seeded in V3). Used as
 * {@code @PreAuthorize("hasAuthority('" + OrganizationPermissions.USER_MANAGE + "')")}.
 * Each later module declares its own codes the same way.
 */
public final class OrganizationPermissions {

    public static final String COMPANY_MANAGE = "COMPANY_MANAGE";
    public static final String BRANCH_VIEW = "BRANCH_VIEW";
    public static final String BRANCH_MANAGE = "BRANCH_MANAGE";
    public static final String USER_VIEW = "USER_VIEW";
    public static final String USER_MANAGE = "USER_MANAGE";
    public static final String ROLE_MANAGE = "ROLE_MANAGE";

    /** Code of the built-in administrator role; its permissions cannot be reduced. */
    public static final String ADMIN_ROLE = "ADMIN";

    private OrganizationPermissions() {
    }
}
