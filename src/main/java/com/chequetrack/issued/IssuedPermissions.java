package com.chequetrack.issued;

/** Permission codes owned by this module (seeded in V10). */
public final class IssuedPermissions {

    public static final String ISSUED_VIEW = "ISSUED_VIEW";
    public static final String ISSUED_MANAGE = "ISSUED_MANAGE";
    /** Stop payment is asked by the Accounts Manager only. */
    public static final String ISSUED_STOP_PAYMENT = "ISSUED_STOP_PAYMENT";

    private IssuedPermissions() {
    }
}
