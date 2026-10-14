package com.chequetrack.common.web;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Formatting helpers for templates, used as {@code ${@displayFormat.ts(entry.occurredAt)}}.
 * Timestamps are stored in UTC and shown in the business time zone.
 */
@Component
public class DisplayFormat {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Clock clock;

    public DisplayFormat(Clock clock) {
        this.clock = clock;
    }

    /** "12/10/2026 15:31:15" in Asia/Dhaka, or an empty string. */
    public String ts(Instant instant) {
        return instant == null ? "" : TIMESTAMP.format(instant.atZone(clock.getZone()));
    }

    /** "dd/MM/yyyy", or an empty string. */
    public String date(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }

    /**
     * Amount with Bangladeshi digit grouping: 1,25,000.00 (last three digits,
     * then groups of two). Always two decimals.
     */
    public String amount(BigDecimal value) {
        if (value == null) {
            return "";
        }
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        String plain = scaled.abs().toPlainString();
        int dot = plain.indexOf('.');
        String whole = plain.substring(0, dot);
        StringBuilder grouped = new StringBuilder();
        int len = whole.length();
        if (len <= 3) {
            grouped.append(whole);
        } else {
            String head = whole.substring(0, len - 3);
            for (int i = 0; i < head.length(); i++) {
                if (i > 0 && (head.length() - i) % 2 == 0) {
                    grouped.append(',');
                }
                grouped.append(head.charAt(i));
            }
            grouped.append(',').append(whole.substring(len - 3));
        }
        return (scaled.signum() < 0 ? "-" : "") + grouped + plain.substring(dot);
    }

    /** A value from an audit change: lists are joined, empty values shown as a dash. */
    public String value(Object value) {
        if (value == null) {
            return "—";
        }
        if (value instanceof Collection<?> c) {
            return c.isEmpty() ? "—" : c.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
        if (value instanceof Boolean b) {
            return b ? "Yes" : "No";
        }
        return String.valueOf(value);
    }
}
