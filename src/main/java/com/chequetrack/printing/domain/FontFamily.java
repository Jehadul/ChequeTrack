package com.chequetrack.printing.domain;

public enum FontFamily {
    HELVETICA("Helvetica (sans)", "Helvetica, Arial, sans-serif"),
    TIMES("Times (serif)", "'Times New Roman', Times, serif"),
    COURIER("Courier (fixed width)", "'Courier New', Courier, monospace");

    private final String label;
    private final String css;

    FontFamily(String label, String css) {
        this.label = label;
        this.css = css;
    }

    public String getLabel() {
        return label;
    }

    /** The matching CSS font stack, for the on-screen designer. */
    public String getCss() {
        return css;
    }
}
