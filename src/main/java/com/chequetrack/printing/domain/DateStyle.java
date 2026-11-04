package com.chequetrack.printing.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public enum DateStyle {
    SLASHED("DD/MM/YYYY", "dd/MM/yyyy"),
    PLAIN("DDMMYYYY (one digit per box)", "ddMMyyyy");

    private final String label;
    private final DateTimeFormatter formatter;

    DateStyle(String label, String pattern) {
        this.label = label;
        this.formatter = DateTimeFormatter.ofPattern(pattern);
    }

    public String getLabel() {
        return label;
    }

    public String format(LocalDate date) {
        return formatter.format(date);
    }
}
