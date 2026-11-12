package com.chequetrack.report.model;

import java.util.ArrayList;
import java.util.List;

public record ReportTable(String title, String subtitle, List<Column> columns, List<List<Object>> rows,
                          List<Object> totals, List<String> notes, boolean truncated) {

    public enum Type { TEXT, DATE, TIMESTAMP, AMOUNT, NUMBER }

    /** {@code width}: relative share of the page width. */
    public record Column(String header, Type type, int width) {
        public static Column text(String header, int width) {
            return new Column(header, Type.TEXT, width);
        }

        public static Column date(String header) {
            return new Column(header, Type.DATE, 9);
        }

        public static Column timestamp(String header) {
            return new Column(header, Type.TIMESTAMP, 13);
        }

        public static Column amount(String header) {
            return new Column(header, Type.AMOUNT, 12);
        }

        public static Column number(String header) {
            return new Column(header, Type.NUMBER, 7);
        }

        public boolean isRightAligned() {
            return type == Type.AMOUNT || type == Type.NUMBER;
        }
    }

    public static Builder builder(String title, String subtitle) {
        return new Builder(title, subtitle);
    }

    public int size() {
        return rows.size();
    }

    public static final class Builder {
        private final String title;
        private final String subtitle;
        private final List<Column> columns = new ArrayList<>();
        private final List<List<Object>> rows = new ArrayList<>();
        private final List<String> notes = new ArrayList<>();
        private List<Object> totals;
        private boolean truncated;

        private Builder(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }

        public Builder column(Column c) {
            columns.add(c);
            return this;
        }

        public Builder row(Object... values) {
            if (values.length != columns.size()) {
                throw new IllegalArgumentException("Row has " + values.length + " values for " + columns.size() + " columns");
            }
            rows.add(java.util.Arrays.asList(values));
            return this;
        }

        public Builder totals(Object... values) {
            this.totals = java.util.Arrays.asList(values);
            return this;
        }

        public Builder note(String note) {
            notes.add(note);
            return this;
        }

        public Builder truncated(boolean truncated) {
            this.truncated = truncated;
            return this;
        }

        public ReportTable build() {
            return new ReportTable(title, subtitle, List.copyOf(columns), rows, totals, List.copyOf(notes), truncated);
        }
    }
}
