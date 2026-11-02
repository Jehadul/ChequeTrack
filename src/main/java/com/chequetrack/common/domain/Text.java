package com.chequetrack.common.domain;

public final class Text {

    private Text() {
    }

    public static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String code(String value) {
        String v = clean(value);
        return v == null ? null : v.toUpperCase();
    }
}
