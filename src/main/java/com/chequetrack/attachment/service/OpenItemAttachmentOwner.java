package com.chequetrack.attachment.service;

import com.chequetrack.security.service.CurrentUser;
import com.chequetrack.setup.SetupPermissions;
import com.chequetrack.setup.domain.OpenItem;
import com.chequetrack.setup.domain.OpenItemStatus;
import com.chequetrack.setup.domain.OpenItemType;
import com.chequetrack.setup.service.OpenItemService;
import org.springframework.stereotype.Component;

/**
 * Attachments of customer invoices and supplier bills (M4). Lives here because M4
 * may not depend on this module; later modules implement their own resolvers.
 */
@Component
public class OpenItemAttachmentOwner implements AttachmentOwnerResolver {

    private final OpenItemService openItemService;

    public OpenItemAttachmentOwner(OpenItemService openItemService) {
        this.openItemService = openItemService;
    }

    @Override
    public String ownerType() {
        return OpenItemService.ENTITY;
    }

    @Override
    public AttachmentOwner resolve(Long ownerId) {
        OpenItem item = openItemService.get(ownerId);  // checks INVOICE_VIEW and branch access
        boolean canModify = CurrentUser.get().hasPermission(SetupPermissions.INVOICE_MANAGE)
                && item.getStatus() != OpenItemStatus.CANCELLED;
        String kind = item.getItemType() == OpenItemType.INVOICE ? "invoices" : "bills";
        return new AttachmentOwner(item.getCompanyId(), item.getBranchId(),
                item.getItemType().getLabel() + " " + item.getNumber() + " · " + item.getParty().getName(),
                "/setup/" + kind + "/" + item.getId(), canModify);
    }
}
