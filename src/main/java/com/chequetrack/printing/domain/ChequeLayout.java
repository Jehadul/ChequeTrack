package com.chequetrack.printing.domain;

import com.chequetrack.common.domain.AmountInWords;
import com.chequetrack.common.domain.Text;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.organization.scope.BranchScopedEntity;
import com.chequetrack.setup.domain.CompanyBankAccount;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "cheque_layout")
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChequeLayout extends BranchScopedEntity {

    public static final String RECORD_TYPE = "ChequeLayout";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private CompanyBankAccount account;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "width_mm", nullable = false)
    private double widthMm;

    @Column(name = "height_mm", nullable = false)
    private double heightMm;

    @Enumerated(EnumType.STRING)
    @Column(name = "words_language", nullable = false, length = 10)
    private AmountInWords.Language wordsLanguage = AmountInWords.Language.ENGLISH;

    @Enumerated(EnumType.STRING)
    @Column(name = "date_format", nullable = false, length = 10)
    private DateStyle dateFormat = DateStyle.PLAIN;

    /** Amount in figures between stars, e.g. **1,25,000.00**, so nothing can be added in front or behind. */
    @Column(name = "protection_stars", nullable = false)
    private boolean protectionStars = true;

    @Column(name = "signatory_company", length = 150)
    private String signatoryCompany;

    @Column(name = "signatory_names", length = 200)
    private String signatoryNames;

    /** Scanned blank leaf shown behind the fields in the designer (an attachment of this layout); never printed. */
    @Column(name = "background_attachment_id")
    private Long backgroundAttachmentId;

    @Column(nullable = false)
    private boolean active;

    @OneToMany(mappedBy = "layout", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<LayoutField> fields = new ArrayList<>();

    /** A new layout with every field placed at a sensible starting point for its size. */
    public static ChequeLayout create(CompanyBankAccount account, String name, double widthMm, double heightMm) {
        ChequeLayout l = new ChequeLayout();
        l.assignScope(account.getCompanyId(), account.getBranchId());
        l.account = account;
        l.name = name.trim();
        l.resize(widthMm, heightMm);
        double w = widthMm, h = heightMm;
        l.fields.add(new LayoutField(l, FieldCode.DATE, w * 0.74, h * 0.09, w * 0.21, 12, TextAlign.LEFT));
        l.fields.add(new LayoutField(l, FieldCode.PAYEE, w * 0.12, h * 0.29, w * 0.68, 12, TextAlign.LEFT));
        l.fields.add(new LayoutField(l, FieldCode.AMOUNT_WORDS, w * 0.17, h * 0.41, w * 0.62, 11, TextAlign.LEFT));
        l.fields.add(new LayoutField(l, FieldCode.AMOUNT_FIGURES, w * 0.75, h * 0.51, w * 0.21, 12, TextAlign.LEFT));
        l.fields.add(new LayoutField(l, FieldCode.CROSSING, w * 0.03, h * 0.30, w * 0.2, 9, TextAlign.CENTER));
        l.fields.add(new LayoutField(l, FieldCode.SIGNATORY, w * 0.68, h * 0.70, w * 0.28, 9, TextAlign.CENTER));
        // field defaults that are not plain positions
        l.field(FieldCode.DATE).update(w * 0.74, h * 0.09, w * 0.21, FontFamily.COURIER, 12, true, TextAlign.LEFT,
                5, null, null, 0, true);
        l.field(FieldCode.AMOUNT_WORDS).update(w * 0.17, h * 0.41, w * 0.62, FontFamily.HELVETICA, 11, false,
                TextAlign.LEFT, 0, w * 0.05, 8.0, 0, true);
        l.field(FieldCode.AMOUNT_FIGURES).update(w * 0.75, h * 0.51, w * 0.21, FontFamily.HELVETICA, 12, true,
                TextAlign.LEFT, 0, null, null, 0, true);
        l.field(FieldCode.CROSSING).update(w * 0.03, h * 0.30, w * 0.2, FontFamily.HELVETICA, 9, true,
                TextAlign.CENTER, 0, null, null, 30, true);
        l.field(FieldCode.SIGNATORY).update(w * 0.68, h * 0.70, w * 0.28, FontFamily.HELVETICA, 9, false,
                TextAlign.CENTER, 0, null, 4.0, 0, true);
        return l;
    }

    /** A copy with every field, for the same or another account; starts inactive. */
    public ChequeLayout copy(CompanyBankAccount targetAccount, String newName) {
        ChequeLayout c = new ChequeLayout();
        c.assignScope(targetAccount.getCompanyId(), targetAccount.getBranchId());
        c.account = targetAccount;
        c.name = newName.trim();
        c.widthMm = widthMm;
        c.heightMm = heightMm;
        c.wordsLanguage = wordsLanguage;
        c.dateFormat = dateFormat;
        c.protectionStars = protectionStars;
        c.signatoryCompany = signatoryCompany;
        c.signatoryNames = signatoryNames;
        fields.forEach(f -> c.fields.add(f.copyFor(c)));
        return c;
    }

    public void update(String name, double widthMm, double heightMm, AmountInWords.Language language, DateStyle dateFormat,
                       boolean protectionStars, String signatoryCompany, String signatoryNames) {
        this.name = name.trim();
        resize(widthMm, heightMm);
        this.wordsLanguage = language;
        this.dateFormat = dateFormat;
        this.protectionStars = protectionStars;
        this.signatoryCompany = Text.clean(signatoryCompany);
        this.signatoryNames = Text.clean(signatoryNames);
    }

    private void resize(double w, double h) {
        if (w < 50 || w > 300 || h < 30 || h > 150) {
            throw new BusinessException("The leaf size must be 50–300 mm wide and 30–150 mm high.");
        }
        this.widthMm = w;
        this.heightMm = h;
    }

    public LayoutField field(FieldCode code) {
        return fields.stream().filter(f -> f.getFieldCode() == code).findFirst()
                .orElseThrow(() -> new IllegalStateException("Layout " + getId() + " has no " + code));
    }

    public void setBackgroundAttachmentId(Long attachmentId) {
        this.backgroundAttachmentId = attachmentId;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
