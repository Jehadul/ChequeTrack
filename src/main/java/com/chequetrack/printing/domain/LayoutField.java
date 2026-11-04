package com.chequetrack.printing.domain;

import com.chequetrack.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "cheque_layout_field")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LayoutField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "layout_id", nullable = false, updatable = false)
    private ChequeLayout layout;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_code", nullable = false, length = 20, updatable = false)
    private FieldCode fieldCode;

    @Column(name = "x_mm", nullable = false)
    private double xMm;

    @Column(name = "y_mm", nullable = false)
    private double yMm;

    @Column(name = "width_mm", nullable = false)
    private double widthMm;

    @Enumerated(EnumType.STRING)
    @Column(name = "font_family", nullable = false, length = 20)
    private FontFamily fontFamily = FontFamily.HELVETICA;

    @Column(name = "font_size_pt", nullable = false)
    private double fontSizePt;

    @Column(nullable = false)
    private boolean bold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TextAlign align = TextAlign.LEFT;

    @Column(name = "char_spacing_mm", nullable = false)
    private double charSpacingMm;

    @Column(name = "line2_x_mm")
    private Double line2XMm;

    @Column(name = "line_gap_mm")
    private Double lineGapMm;

    @Column(name = "angle_deg", nullable = false)
    private double angleDeg;

    @Column(nullable = false)
    private boolean printed = true;

    LayoutField(ChequeLayout layout, FieldCode code, double x, double y, double width, double size, TextAlign align) {
        this.layout = layout;
        this.fieldCode = code;
        this.xMm = x;
        this.yMm = y;
        this.widthMm = width;
        this.fontSizePt = size;
        this.align = align;
    }

    /** A copy for another layout. */
    LayoutField copyFor(ChequeLayout other) {
        LayoutField f = new LayoutField(other, fieldCode, xMm, yMm, widthMm, fontSizePt, align);
        f.fontFamily = fontFamily;
        f.bold = bold;
        f.charSpacingMm = charSpacingMm;
        f.line2XMm = line2XMm;
        f.lineGapMm = lineGapMm;
        f.angleDeg = angleDeg;
        f.printed = printed;
        return f;
    }

    /** Dragged in the designer. */
    public void moveTo(double x, double y, double width) {
        check(x, y, width);
        this.xMm = round(x);
        this.yMm = round(y);
        this.widthMm = round(width);
    }

    public void update(double x, double y, double width, FontFamily family, double size, boolean bold, TextAlign align,
                       double charSpacing, Double line2X, Double lineGap, double angle, boolean printed) {
        check(x, y, width);
        if (size < 5 || size > 30) {
            throw new BusinessException(fieldCode.getLabel() + ": the font size must be 5 to 30 pt.");
        }
        if (charSpacing < 0 || charSpacing > 20) {
            throw new BusinessException(fieldCode.getLabel() + ": the digit spacing must be 0 to 20 mm.");
        }
        if (angle < -90 || angle > 90) {
            throw new BusinessException(fieldCode.getLabel() + ": the angle must be between -90 and 90 degrees.");
        }
        this.xMm = round(x);
        this.yMm = round(y);
        this.widthMm = round(width);
        this.fontFamily = family;
        this.fontSizePt = round(size);
        this.bold = bold;
        this.align = align;
        this.charSpacingMm = round(charSpacing);
        this.line2XMm = line2X == null ? null : round(line2X);
        this.lineGapMm = lineGap == null ? null : round(lineGap);
        this.angleDeg = round(angle);
        this.printed = printed;
    }

    private void check(double x, double y, double width) {
        if (x < 0 || y < 0 || width <= 0 || x > layout.getWidthMm() || y > layout.getHeightMm()) {
            throw new BusinessException(fieldCode.getLabel() + " must stay on the leaf ("
                    + layout.getWidthMm() + " × " + layout.getHeightMm() + " mm).");
        }
    }

    /** Positions are kept to 0.1 mm, far finer than any printer feeds paper. */
    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
