package com.chequetrack.common.domain;

/** Small text helpers for entity setters. */
public final class Text {

    private Text() {
    }

    /** Trimmed value, or null when blank. */
    public static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Trimmed upper-case code, or null when blank. */
    public static String code(String value) {
        String v = clean(value);
        return v == null ? null : v.toUpperCase();
    }
}
