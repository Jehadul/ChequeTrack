package com.chequetrack.printing.service;

import com.chequetrack.attachment.domain.Attachment;
import com.chequetrack.attachment.domain.AttachmentCategory;
import com.chequetrack.attachment.service.AttachmentService;
import com.chequetrack.attachment.service.FileTypeDetector;
import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.event.Changes;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.organization.scope.BranchScope;
import com.chequetrack.printing.PrintingPermissions;
import com.chequetrack.printing.domain.ChequeLayout;
import com.chequetrack.printing.domain.FieldCode;
import com.chequetrack.printing.domain.LayoutField;
import com.chequetrack.printing.dto.FieldsForm;
import com.chequetrack.printing.dto.LayoutForm;
import com.chequetrack.printing.repository.ChequeLayoutRepository;
import com.chequetrack.setup.domain.CompanyBankAccount;
import com.chequetrack.setup.service.CompanyAccountService;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

@Service
public class LayoutService {

    private static final String MANAGE = "hasAuthority('" + PrintingPermissions.LAYOUT_MANAGE + "')";

    private final ChequeLayoutRepository layoutRepository;
    private final CompanyAccountService accountService;
    private final AttachmentService attachmentService;
    private final BranchScope branchScope;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;

    public LayoutService(ChequeLayoutRepository layoutRepository,
                         CompanyAccountService accountService,
                         AttachmentService attachmentService,
                         BranchScope branchScope,
                         EntityManager entityManager,
                         ApplicationEventPublisher events) {
        this.layoutRepository = layoutRepository;
        this.accountService = accountService;
        this.attachmentService = attachmentService;
        this.branchScope = branchScope;
        this.entityManager = entityManager;
        this.events = events;
    }

    // ---- reading -------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize(MANAGE)
    public List<ChequeLayout> list() {
        return layoutRepository.findAllVisible();
    }

    @Transactional(readOnly = true)
    @PreAuthorize(MANAGE)
    public ChequeLayout get(Long id) {
        return load(id);
    }

    /** The layout cheques of this account are printed with, or null (used when printing). */
    @Transactional(readOnly = true)
    public ChequeLayout activeFor(Long accountId) {
        ChequeLayout layout = layoutRepository.findActive(accountId).orElse(null);
        if (layout != null) {
            layout.getFields().size();
            layout.getAccount().getLabel();
        }
        return layout;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(MANAGE)
    public LayoutForm editForm(Long id) {
        ChequeLayout l = load(id);
        LayoutForm form = new LayoutForm();
        form.setId(l.getId());
        form.setVersion(l.getVersion());
        form.setAccountId(l.getAccount().getId());
        form.setName(l.getName());
        form.setWidthMm(l.getWidthMm());
        form.setHeightMm(l.getHeightMm());
        form.setWordsLanguage(l.getWordsLanguage());
        form.setDateFormat(l.getDateFormat());
        form.setProtectionStars(l.isProtectionStars());
        form.setSignatoryCompany(l.getSignatoryCompany());
        form.setSignatoryNames(l.getSignatoryNames());
        return form;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(MANAGE)
    public FieldsForm fieldsForm(Long id) {
        ChequeLayout l = load(id);
        FieldsForm form = new FieldsForm();
        for (FieldCode code : FieldCode.values()) {
            LayoutField f = l.field(code);
            FieldsForm.Row r = new FieldsForm.Row();
            r.setFieldCode(code);
            r.setX(f.getXMm());
            r.setY(f.getYMm());
            r.setWidth(f.getWidthMm());
            r.setFontFamily(f.getFontFamily());
            r.setFontSize(f.getFontSizePt());
            r.setBold(f.isBold());
            r.setAlign(f.getAlign());
            r.setCharSpacing(f.getCharSpacingMm());
            r.setLine2X(f.getLine2XMm());
            r.setLineGap(f.getLineGapMm());
            r.setAngle(f.getAngleDeg());
            r.setPrinted(f.isPrinted());
            form.getRows().add(r);
        }
        return form;
    }

    // ---- changes -------------------------------------------------------------------------------

    /** A new layout with starting positions; it becomes active if the account has none yet. */
    @Transactional
    @PreAuthorize(MANAGE)
    public ChequeLayout create(LayoutForm form) {
        CompanyBankAccount account = accountService.get(form.getAccountId());  // branch check
        ChequeLayout layout = ChequeLayout.create(account, form.getName(), form.getWidthMm(), form.getHeightMm());
        layout.update(form.getName(), form.getWidthMm(), form.getHeightMm(), form.getWordsLanguage(), form.getDateFormat(),
                form.isProtectionStars(), form.getSignatoryCompany(), form.getSignatoryNames());
        if (layoutRepository.findActive(account.getId()).isEmpty()) {
            layout.activate();
        }
        layoutRepository.save(layout);
        events.publishEvent(AuditEvent.of("LAYOUT_CREATED", ChequeLayout.RECORD_TYPE, layout.getId(),
                "Created cheque layout '" + layout.getName() + "' (" + layout.getWidthMm() + " × " + layout.getHeightMm()
                        + " mm) for " + account.getLabel() + (layout.isActive() ? ", active" : "")));
        return layout;
    }

    @Transactional
    @PreAuthorize(MANAGE)
    public void update(Long id, LayoutForm form) {
        ChequeLayout l = load(id);
        l.checkVersion(form.getVersion());
        Changes changes = new Changes()
                .add("name", l.getName(), form.getName().trim())
                .add("size", l.getWidthMm() + " × " + l.getHeightMm(), form.getWidthMm() + " × " + form.getHeightMm())
                .add("words", l.getWordsLanguage(), form.getWordsLanguage())
                .add("date", l.getDateFormat(), form.getDateFormat())
                .add("stars", l.isProtectionStars(), form.isProtectionStars())
                .add("signatoryCompany", l.getSignatoryCompany(), form.getSignatoryCompany())
                .add("signatoryNames", l.getSignatoryNames(), form.getSignatoryNames());
        l.update(form.getName(), form.getWidthMm(), form.getHeightMm(), form.getWordsLanguage(), form.getDateFormat(),
                form.isProtectionStars(), form.getSignatoryCompany(), form.getSignatoryNames());
        if (!changes.isEmpty()) {
            events.publishEvent(AuditEvent.of("LAYOUT_UPDATED", ChequeLayout.RECORD_TYPE, l.getId(),
                    "Updated cheque layout '" + l.getName() + "'", changes));
        }
    }

    /** The exact values typed in the field table. */
    @Transactional
    @PreAuthorize(MANAGE)
    public void saveFields(Long id, FieldsForm form) {
        ChequeLayout l = load(id);
        Changes changes = new Changes();
        for (FieldsForm.Row r : form.getRows()) {
            LayoutField f = l.field(r.getFieldCode());
            String before = describe(f);
            f.update(r.getX(), r.getY(), r.getWidth(), r.getFontFamily(), r.getFontSize(), r.isBold(), r.getAlign(),
                    r.getCharSpacing() == null ? 0 : r.getCharSpacing(), r.getLine2X(), r.getLineGap(),
                    r.getAngle() == null ? 0 : r.getAngle(), r.isPrinted());
            changes.add(r.getFieldCode().name(), before, describe(f));
        }
        if (!changes.isEmpty()) {
            events.publishEvent(AuditEvent.of("LAYOUT_FIELDS_UPDATED", ChequeLayout.RECORD_TYPE, l.getId(),
                    "Updated field settings of layout '" + l.getName() + "'", changes));
        }
    }

    /** A field dropped or resized in the designer. */
    @Transactional
    @PreAuthorize(MANAGE)
    public LayoutField moveField(Long id, FieldCode code, double x, double y, double width) {
        ChequeLayout l = load(id);
        LayoutField f = l.field(code);
        String before = describe(f);
        f.moveTo(x, y, width);
        events.publishEvent(AuditEvent.of("LAYOUT_FIELD_MOVED", ChequeLayout.RECORD_TYPE, l.getId(),
                "Moved " + code.getLabel().toLowerCase() + " on layout '" + l.getName() + "'",
                new Changes().add(code.name(), before, describe(f))));
        return f;
    }

    /** One active layout per account: the others of that account are switched off first. */
    @Transactional
    @PreAuthorize(MANAGE)
    public void activate(Long id) {
        ChequeLayout l = load(id);
        if (l.isActive()) {
            return;
        }
        List<ChequeLayout> others = layoutRepository.findOtherActive(l.getAccount().getId(), l.getId());
        others.forEach(ChequeLayout::deactivate);
        entityManager.flush();
        l.activate();
        try {
            entityManager.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("Another layout of this account was activated at the same moment. Reload and try again.");
        }
        events.publishEvent(AuditEvent.of("LAYOUT_ACTIVATED", ChequeLayout.RECORD_TYPE, l.getId(),
                "Layout '" + l.getName() + "' is now used for " + l.getAccount().getLabel()
                        + (others.isEmpty() ? "" : " (replacing '" + others.get(0).getName() + "')")));
    }

    /** A copy to adjust, for the same account or another one (e.g. a second account at the same bank). */
    @Transactional
    @PreAuthorize(MANAGE)
    public ChequeLayout copy(Long id, Long targetAccountId, String name) {
        ChequeLayout source = load(id);
        CompanyBankAccount target = targetAccountId == null ? source.getAccount() : accountService.get(targetAccountId);
        String newName = name == null || name.isBlank() ? "Copy of " + source.getName() : name.trim();
        if (newName.length() > 100) {
            throw new BusinessException("The name must be at most 100 characters.");
        }
        ChequeLayout copy = source.copy(target, newName);
        if (layoutRepository.findActive(target.getId()).isEmpty()) {
            copy.activate();
        }
        layoutRepository.save(copy);
        events.publishEvent(AuditEvent.of("LAYOUT_COPIED", ChequeLayout.RECORD_TYPE, copy.getId(),
                "Copied layout '" + source.getName() + "' to '" + copy.getName() + "' for " + target.getLabel()));
        return copy;
    }

    /** Scanned blank leaf shown behind the fields in the designer. Stored as an attachment of the layout. */
    @Transactional
    @PreAuthorize(MANAGE)
    public void setBackground(Long id, MultipartFile file) {
        ChequeLayout l = load(id);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Choose the scanned image of a blank leaf.");
        }
        try (InputStream in = file.getInputStream()) {
            String mime = FileTypeDetector.detect(in.readNBytes(FileTypeDetector.HEADER_LENGTH))
                    .map(FileTypeDetector.FileType::mimeType).orElse("");
            if (!mime.startsWith("image/")) {
                throw new BusinessException("Upload the scan as a JPEG or PNG image.");
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        Attachment a = attachmentService.upload(ChequeLayout.RECORD_TYPE, l.getId(), AttachmentCategory.CHEQUE_SCAN,
                "Blank leaf for layout '" + l.getName() + "'", file);
        l.setBackgroundAttachmentId(a.getId());
    }

    // ---- helpers -------------------------------------------------------------------------------

    private ChequeLayout load(Long id) {
        ChequeLayout l = branchScope.require(layoutRepository.findById(id), "Cheque layout", id);
        l.getFields().size();
        l.getAccount().getBankBranch().getBank().getName();
        return l;
    }

    private static String describe(LayoutField f) {
        return String.format("x %.1f, y %.1f, w %.1f, %s %.1fpt%s, %s%s%s%s",
                f.getXMm(), f.getYMm(), f.getWidthMm(), f.getFontFamily().name().toLowerCase(), f.getFontSizePt(),
                f.isBold() ? " bold" : "", f.getAlign().name().toLowerCase(),
                f.getCharSpacingMm() > 0 ? ", spacing " + f.getCharSpacingMm() : "",
                f.getAngleDeg() != 0 ? ", angle " + f.getAngleDeg() : "",
                f.isPrinted() ? "" : ", not printed");
    }
}
