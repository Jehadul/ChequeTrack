package com.chequetrack.setup.domain;

public enum OpenItemType {
    /** Customer owes us; settled by received cheques. */
    INVOICE("Invoice", PartyType.CUSTOMER),
    /** We owe a supplier; settled by issued cheques. */
    BILL("Bill", PartyType.SUPPLIER);

    private final String label;
    private final PartyType partyRole;

    OpenItemType(String label, PartyType partyRole) {
        this.label = label;
        this.partyRole = partyRole;
    }

    public String getLabel() {
        return label;
    }

    /** Whether a party of the given type may hold this kind of item. */
    public boolean allowedFor(PartyType type) {
        return partyRole == PartyType.CUSTOMER ? type.isCustomer() : type.isSupplier();
    }
}
