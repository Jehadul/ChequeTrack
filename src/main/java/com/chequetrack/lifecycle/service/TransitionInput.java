package com.chequetrack.lifecycle.service;

import java.util.Map;

/**
 * What the user gave for a status change.
 *
 * @param reason  why (required by some actions); kept in status history and the audit log
 * @param payload extra values of the action, e.g. {"newDate": "2026-11-30"} for a defer;
 *                stored with an approval request and handed back when it is approved
 */
public record TransitionInput(String reason, Map<String, Object> payload) {

    public static TransitionInput of(String reason) {
        return new TransitionInput(reason, Map.of());
    }

    public static TransitionInput of(String reason, Map<String, Object> payload) {
        return new TransitionInput(reason, payload == null ? Map.of() : payload);
    }

    public Object value(String key) {
        return payload == null ? null : payload.get(key);
    }
}
