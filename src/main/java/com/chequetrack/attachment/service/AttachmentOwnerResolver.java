package com.chequetrack.attachment.service;

/**
 * Connects attachments to one kind of record. Each module that lets users attach
 * files implements this for its records (as a Spring bean). The resolver loads the
 * record through that module's own service, so the module's branch and permission
 * rules decide who may see and change the attachments.
 */
public interface AttachmentOwnerResolver {

    /** The owner type stored in {@code attachment.owner_type}, e.g. "OpenItem". */
    String ownerType();

    /**
     * @return details of the record when the current user may see it
     * @throws com.chequetrack.common.exception.NotFoundException when it does not exist or is not visible
     */
    AttachmentOwner resolve(Long ownerId);

    /**
     * @param companyId  company of the record
     * @param branchId   branch of the record, or null for company-wide records
     * @param label      shown on the attachments page, e.g. "Invoice INV-1001 · Rahman Traders"
     * @param url        page of the record, for the "back" link
     * @param canModify  whether the current user may add or remove attachments
     */
    record AttachmentOwner(Long companyId, Long branchId, String label, String url, boolean canModify) {
    }
}
