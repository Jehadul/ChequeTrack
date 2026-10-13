package com.chequetrack.common.event;

/**
 * A record moved from one status to another. Recorded in {@code status_history}
 * by the audit module within the same transaction.
 *
 * @param fromStatus null when the record is created
 * @param reason     why; required by most business rules (bounce, void, defer, disable...)
 */
public record StatusChangedEvent(String entityType, Long entityId, Long companyId, Long branchId,
                                 String fromStatus, String toStatus, String reason) {
}
