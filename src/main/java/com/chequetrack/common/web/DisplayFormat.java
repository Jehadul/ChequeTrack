package com.chequetrack.common.web;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
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

    private final Clock clock;

    public DisplayFormat(Clock clock) {
        this.clock = clock;
    }

    /** "12/10/2026 15:31:15" in Asia/Dhaka, or an empty string. */
    public String ts(Instant instant) {
        return instant == null ? "" : TIMESTAMP.format(instant.atZone(clock.getZone()));
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
