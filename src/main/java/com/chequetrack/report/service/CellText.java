package com.chequetrack.report.service;

import com.chequetrack.common.web.DisplayFormat;
import com.chequetrack.report.model.ReportTable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Component
public class CellText {

    private final DisplayFormat format;

    public CellText(DisplayFormat format) {
        this.format = format;
    }

    public String format(Object v, ReportTable.Type type) {
        if (v == null) {
            return "";
        }
        if (v instanceof LocalDate d) {
            return format.date(d);
        }
        if (v instanceof Instant i) {
            return format.ts(i);
        }
        if (v instanceof BigDecimal b && type == ReportTable.Type.AMOUNT) {
            return format.amount(b);
        }
        return String.valueOf(v);
    }
}
