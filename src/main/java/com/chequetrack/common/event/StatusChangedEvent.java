package com.chequetrack.common.event;

public record StatusChangedEvent(String entityType, Long entityId, Long companyId, Long branchId,
        String fromStatus, String toStatus, String reason) {
}
