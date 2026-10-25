package com.chequetrack.issued.service;

import com.chequetrack.attachment.service.AttachmentOwnerResolver;
import com.chequetrack.issued.IssuedPermissions;
import com.chequetrack.issued.domain.IssuedCheque;
import com.chequetrack.security.service.CurrentUser;
import org.springframework.stereotype.Component;

/** Purchase orders, bills and copies of issued cheques (M5 attachments). */
@Component
public class IssuedChequeAttachmentOwner implements AttachmentOwnerResolver {

    private final IssuedChequeService chequeService;

    public IssuedChequeAttachmentOwner(IssuedChequeService chequeService) {
        this.chequeService = chequeService;
    }

    @Override
    public String ownerType() {
        return IssuedCheque.RECORD_TYPE;
    }

    @Override
    public AttachmentOwner resolve(Long ownerId) {
        IssuedCheque c = chequeService.get(ownerId);  // ISSUED_VIEW + branch check
        return new AttachmentOwner(c.getCompanyId(), c.getBranchId(), c.lifecycleLabel(), "/issued/" + c.getId(),
                CurrentUser.get().hasPermission(IssuedPermissions.ISSUED_MANAGE));
    }
}
