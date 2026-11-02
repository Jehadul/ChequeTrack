package com.chequetrack.common.event;

import java.util.Map;

public record AuditEvent(String action, String entityType, Object entityId, String summary,
        Map<String, Object> details) {

    public static AuditEvent of(String action, String entityType, Object entityId, String summary) {
        return new AuditEvent(action, entityType, entityId, summary, null);
    }

    public static AuditEvent of(String action, String entityType, Object entityId, String summary, Changes changes) {
        return new AuditEvent(action, entityType, entityId, summary, changes.isEmpty() ? null : changes.asMap());
    }
}
