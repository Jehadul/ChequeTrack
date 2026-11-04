package com.chequetrack.printing.domain;

import com.chequetrack.common.domain.BaseEntity;
import com.chequetrack.common.domain.Text;
import com.chequetrack.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Entity
@Table(name = "printer_profile")
@SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrinterProfile extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "offset_x_mm", nullable = false)
    private double offsetXMm;

    @Column(name = "offset_y_mm", nullable = false)
    private double offsetYMm;

    @Column(length = 200)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    public static PrinterProfile create(Long companyId) {
        PrinterProfile p = new PrinterProfile();
        p.companyId = companyId;
        return p;
    }

    public void update(String name, double offsetX, double offsetY, String notes) {
        if (Math.abs(offsetX) > 20 || Math.abs(offsetY) > 20) {
            throw new BusinessException("An offset of more than 20 mm means the paper is not fed correctly. Check the printer tray.");
        }
        this.name = name.trim();
        this.offsetXMm = Math.round(offsetX * 10) / 10.0;
        this.offsetYMm = Math.round(offsetY * 10) / 10.0;
        this.notes = Text.clean(notes);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /** "Office laser (+1.5 → / −0.5 ↓ mm)" */
    public String getLabel() {
        return name + " (" + signed(offsetXMm) + " → / " + signed(offsetYMm) + " ↓ mm)";
    }

    private static String signed(double v) {
        return (v > 0 ? "+" : "") + v;
    }
}
