package com.chequetrack.received.service;

import com.chequetrack.attachment.service.AttachmentOwnerResolver;
import com.chequetrack.received.ReceivedPermissions;
import com.chequetrack.received.domain.ReceivedCheque;
import com.chequetrack.security.service.CurrentUser;
import org.springframework.stereotype.Component;

/** Scanned images and documents of received cheques (M5 attachments). */
@Component
public class ReceivedChequeAttachmentOwner implements AttachmentOwnerResolver {

    private final ReceivedChequeService chequeService;

    public ReceivedChequeAttachmentOwner(ReceivedChequeService chequeService) {
        this.chequeService = chequeService;
    }

    @Override
    public String ownerType() {
        return ReceivedCheque.RECORD_TYPE;
    }

    @Override
    public AttachmentOwner resolve(Long ownerId) {
        ReceivedCheque c = chequeService.get(ownerId);  // RECEIVED_VIEW + branch check
        return new AttachmentOwner(c.getCompanyId(), c.getBranchId(), c.lifecycleLabel(), "/received/" + c.getId(),
                CurrentUser.get().hasPermission(ReceivedPermissions.RECEIVED_MANAGE));
    }
}
