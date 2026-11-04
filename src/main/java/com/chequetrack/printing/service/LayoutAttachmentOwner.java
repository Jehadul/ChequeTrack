package com.chequetrack.printing.service;

import com.chequetrack.attachment.service.AttachmentOwnerResolver;
import com.chequetrack.organization.scope.BranchScope;
import com.chequetrack.printing.PrintingPermissions;
import com.chequetrack.printing.domain.ChequeLayout;
import com.chequetrack.printing.repository.ChequeLayoutRepository;
import com.chequetrack.security.service.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class LayoutAttachmentOwner implements AttachmentOwnerResolver {

    private final ChequeLayoutRepository layoutRepository;
    private final BranchScope branchScope;

    public LayoutAttachmentOwner(ChequeLayoutRepository layoutRepository, BranchScope branchScope) {
        this.layoutRepository = layoutRepository;
        this.branchScope = branchScope;
    }

    @Override
    public String ownerType() {
        return ChequeLayout.RECORD_TYPE;
    }

    @Override
    public AttachmentOwner resolve(Long ownerId) {
        if (!CurrentUser.get().hasPermission(PrintingPermissions.LAYOUT_MANAGE)) {
            throw new AccessDeniedException("Missing permission " + PrintingPermissions.LAYOUT_MANAGE);
        }
        ChequeLayout l = branchScope.require(layoutRepository.findById(ownerId), "Cheque layout", ownerId);
        return new AttachmentOwner(l.getCompanyId(), l.getBranchId(), "Layout " + l.getName(),
                "/printing/layouts/" + l.getId(), true);
    }
}
