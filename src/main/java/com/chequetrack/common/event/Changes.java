package com.chequetrack.common.event;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Collects the fields that really changed, for the audit log:
 * <pre>
 *   Changes changes = new Changes()
 *       .add("name", branch.getName(), form.getName())
 *       .add("phone", branch.getPhone(), form.getPhone());
 * </pre>
 * Unchanged fields are skipped. Collections are compared and stored sorted.
 * Never add passwords or other secrets.
 */
public class Changes {

    private final Map<String, Object> fields = new LinkedHashMap<>();

    public Changes add(String field, Object from, Object to) {
        Object a = normalize(from);
        Object b = normalize(to);
        if (!Objects.equals(a, b)) {
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("from", a);
            change.put("to", b);
            fields.put(field, change);
        }
        return this;
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    /** Blank strings count as "no value"; collections are compared as sorted sets. */
    private static Object normalize(Object value) {
        if (value instanceof String s) {
            String trimmed = s.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        if (value instanceof Collection<?> c) {
            TreeSet<String> sorted = new TreeSet<>();
            c.forEach(item -> sorted.add(String.valueOf(item)));
            return sorted.stream().toList();
        }
        return value;
    }
}
