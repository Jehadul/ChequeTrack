package com.chequetrack.attachment.service;

public interface AttachmentOwnerResolver {

    String ownerType();

    AttachmentOwner resolve(Long ownerId);

    record AttachmentOwner(Long companyId, Long branchId, String label, String url, boolean canModify) {
    }
}
