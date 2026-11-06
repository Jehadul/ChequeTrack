package com.chequetrack.notification.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public record Digest(Recipients.BranchInfo branch, LocalDate date, int leadDays, List<Section> sections) {

    /** One line of a section; {@code link} is absolute so it works from an email. */
    public record Item(String number, String party, BigDecimal amount, LocalDate date, String note, String link) {
    }

    /** {@code alert}: shown in red (exceptions), the others are routine reminders. */
    public record Section(String key, String title, boolean alert, List<Item> items) {
    }

    public static Digest empty(Recipients.BranchInfo branch, LocalDate date, int leadDays) {
        return new Digest(branch, date, leadDays, new ArrayList<>());
    }

    public void add(String key, String title, boolean alert, List<Item> items) {
        if (!items.isEmpty()) {
            sections.add(new Section(key, title, alert, items));
        }
    }

    public boolean isEmpty() {
        return sections.isEmpty();
    }

    public int itemCount() {
        return sections.stream().mapToInt(s -> s.items().size()).sum();
    }

    /** "3 to deposit, 1 bounced" for the subject line and the in-app notification. */
    public String summary() {
        List<String> parts = new ArrayList<>();
        for (Section s : sections) {
            parts.add(s.items().size() + " " + s.key());
        }
        return String.join(", ", parts);
    }
}
