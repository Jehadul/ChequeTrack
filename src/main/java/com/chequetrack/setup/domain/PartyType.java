package com.chequetrack.setup.domain;

public enum PartyType {
    CUSTOMER("Customer"),
    SUPPLIER("Supplier"),
    BOTH("Customer and supplier");

    private final String label;

    PartyType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isCustomer() {
        return this != SUPPLIER;
    }

    public boolean isSupplier() {
        return this != CUSTOMER;
    }
}
