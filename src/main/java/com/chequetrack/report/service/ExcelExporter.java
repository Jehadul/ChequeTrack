package com.chequetrack.report.service;

import com.chequetrack.report.model.ReportTable;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
public class ExcelExporter {

    /** Lakh/crore grouping as an Excel number format. */
    private static final String TAKA = "[>=10000000]##\\,##\\,##\\,##0.00;[>=100000]##\\,##\\,##0.00;##,##0.00";

    private final Clock clock;

    public ExcelExporter(Clock clock) {
        this.clock = clock;
    }

    public byte[] export(ReportTable t, String footer) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(WorkbookUtil.createSafeSheetName(t.title()));
            CreationHelper ch = wb.getCreationHelper();
            Styles s = new Styles(wb, ch);

            int r = 0;
            cell(sheet.createRow(r++), 0, t.title(), s.title);
            cell(sheet.createRow(r++), 0, t.subtitle(), null);
            r++;
            int headerRow = r;
            Row header = sheet.createRow(r++);
            for (int i = 0; i < t.columns().size(); i++) {
                cell(header, i, t.columns().get(i).header(), s.header);
            }
            for (List<Object> values : t.rows()) {
                Row row = sheet.createRow(r++);
                for (int i = 0; i < values.size(); i++) {
                    value(row, i, values.get(i), t.columns().get(i).type(), s, false);
                }
            }
            int lastData = r - 1;
            if (t.totals() != null) {
                Row row = sheet.createRow(r++);
                for (int i = 0; i < t.totals().size(); i++) {
                    value(row, i, t.totals().get(i), i == 0 ? ReportTable.Type.TEXT : t.columns().get(i).type(), s, true);
                }
            }
            r++;
            if (t.rows().isEmpty()) {
                cell(sheet.createRow(r++), 0, "No cheques match these filters.", null);
            }
            for (String n : t.notes()) {
                cell(sheet.createRow(r++), 0, n, null);
            }
            if (t.truncated()) {
                cell(sheet.createRow(r++), 0, "Only the first " + t.size() + " rows are included; narrow the filters to see the rest.", null);
            }
            cell(sheet.createRow(r), 0, footer, null);

            for (int i = 0; i < t.columns().size(); i++) {
                sheet.setColumnWidth(i, Math.min(60, Math.max(10, t.columns().get(i).width() * 2)) * 256);
            }
            sheet.createFreezePane(0, headerRow + 1);
            if (!t.rows().isEmpty()) {
                sheet.setAutoFilter(new CellRangeAddress(headerRow, lastData, 0, t.columns().size() - 1));
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not produce the Excel report", ex);
        }
    }

    private void value(Row row, int i, Object v, ReportTable.Type type, Styles s, boolean total) {
        Cell c = row.createCell(i);
        if (v == null) {
            if (total) {
                c.setCellStyle(s.totalText);
            }
            return;
        }
        if (v instanceof LocalDate d) {
            c.setCellValue(d);
            c.setCellStyle(s.date);
        } else if (v instanceof Instant in) {
            c.setCellValue(in.atZone(clock.getZone()).toLocalDateTime());
            c.setCellStyle(s.timestamp);
        } else if (v instanceof BigDecimal b && type == ReportTable.Type.AMOUNT) {
            c.setCellValue(b.doubleValue());
            c.setCellStyle(total ? s.totalAmount : s.amount);
        } else if (v instanceof Number n) {
            c.setCellValue(n.doubleValue());
            c.setCellStyle(total ? s.totalNumber : null);
        } else {
            c.setCellValue(String.valueOf(v));
            if (total) {
                c.setCellStyle(s.totalText);
            }
        }
    }

    private static void cell(Row row, int i, String text, CellStyle style) {
        Cell c = row.createCell(i);
        c.setCellValue(text == null ? "" : text);
        if (style != null) {
            c.setCellStyle(style);
        }
    }

    private static final class Styles {
        final CellStyle title;
        final CellStyle header;
        final CellStyle date;
        final CellStyle timestamp;
        final CellStyle amount;
        final CellStyle totalAmount;
        final CellStyle totalNumber;
        final CellStyle totalText;

        Styles(XSSFWorkbook wb, CreationHelper ch) {
            Font big = wb.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 14);
            title = wb.createCellStyle();
            title.setFont(big);

            Font bold = wb.createFont();
            bold.setBold(true);
            header = wb.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);

            date = wb.createCellStyle();
            date.setDataFormat(ch.createDataFormat().getFormat("dd/mm/yyyy"));
            date.setAlignment(HorizontalAlignment.LEFT);
            timestamp = wb.createCellStyle();
            timestamp.setDataFormat(ch.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));
            timestamp.setAlignment(HorizontalAlignment.LEFT);
            amount = wb.createCellStyle();
            amount.setDataFormat(ch.createDataFormat().getFormat(TAKA));

            totalAmount = wb.createCellStyle();
            totalAmount.cloneStyleFrom(amount);
            totalAmount.setFont(bold);
            totalAmount.setBorderTop(BorderStyle.THIN);
            totalNumber = wb.createCellStyle();
            totalNumber.setFont(bold);
            totalNumber.setBorderTop(BorderStyle.THIN);
            totalText = wb.createCellStyle();
            totalText.setFont(bold);
            totalText.setBorderTop(BorderStyle.THIN);
        }
    }
}
