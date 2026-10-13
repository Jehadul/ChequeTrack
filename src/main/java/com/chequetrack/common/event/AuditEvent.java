package com.chequetrack.common.event;

import java.util.Map;

/**
 * "Something worth auditing happened." Any module publishes it with
 * {@code ApplicationEventPublisher}; the audit module (M3) records it inside the
 * same transaction, so a change and its audit entry are saved together or not at all.
 * The current user, company, time and IP address are added by the recorder.
 *
 * @param action     short code, e.g. {@code USER_UPDATED}
 * @param entityType record type, e.g. {@code User}; may be null for general actions
 * @param entityId   record id; may be null
 * @param summary    one human-readable sentence
 * @param details    optional changed fields, see {@link Changes}; never passwords or secrets
 */
public record AuditEvent(String action, String entityType, Object entityId, String summary,
                         Map<String, Object> details) {

    public static AuditEvent of(String action, String entityType, Object entityId, String summary) {
        return new AuditEvent(action, entityType, entityId, summary, null);
    }

    public static AuditEvent of(String action, String entityType, Object entityId, String summary, Changes changes) {
        return new AuditEvent(action, entityType, entityId, summary, changes.isEmpty() ? null : changes.asMap());
    }
}
