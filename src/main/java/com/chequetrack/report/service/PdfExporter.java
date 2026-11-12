package com.chequetrack.report.service;

import com.chequetrack.report.model.ReportTable;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignElement;
import net.sf.jasperreports.engine.design.JRDesignExpression;
import net.sf.jasperreports.engine.design.JRDesignField;
import net.sf.jasperreports.engine.design.JRDesignSection;
import net.sf.jasperreports.engine.design.JRDesignStaticText;
import net.sf.jasperreports.engine.design.JRDesignStyle;
import net.sf.jasperreports.engine.design.JRDesignTextField;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.type.HorizontalTextAlignEnum;
import net.sf.jasperreports.engine.type.ModeEnum;
import net.sf.jasperreports.engine.type.OrientationEnum;
import net.sf.jasperreports.engine.type.StretchTypeEnum;
import net.sf.jasperreports.engine.type.TextAdjustEnum;
import net.sf.jasperreports.engine.type.VerticalTextAlignEnum;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PdfExporter {

    private static final int PAGE_W = 842;
    private static final int PAGE_H = 595;
    private static final int MARGIN = 28;
    private static final int WIDTH = PAGE_W - 2 * MARGIN;
    private static final String FONT = "DejaVu Sans";
    private static final Color HEADER_BG = new Color(0xE9, 0xEE, 0xF4);
    private static final Color BRAND = new Color(0x1F, 0x4E, 0x79);

    private final CellText cellText;

    public PdfExporter(CellText cellText) {
        this.cellText = cellText;
    }

    public byte[] export(ReportTable table, String footer) {
        try {
            JasperReport report = JasperCompileManager.compileReport(design(table, footer));
            List<Map<String, ?>> rows = new ArrayList<>();
            for (List<Object> r : table.rows()) {
                Map<String, Object> m = new HashMap<>();
                for (int i = 0; i < r.size(); i++) {
                    m.put("c" + i, cellText.format(r.get(i), table.columns().get(i).type()));
                }
                rows.add(m);
            }
            JasperPrint print = JasperFillManager.fillReport(report, new HashMap<>(), new JRMapCollectionDataSource(rows));
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException ex) {
            throw new IllegalStateException("Could not produce the PDF report", ex);
        }
    }

    private JasperDesign design(ReportTable t, String footer) throws JRException {
        JasperDesign d = new JasperDesign();
        d.setName("chequetrack-report");
        d.setPageWidth(PAGE_W);
        d.setPageHeight(PAGE_H);
        d.setOrientation(OrientationEnum.LANDSCAPE);
        d.setLeftMargin(MARGIN);
        d.setRightMargin(MARGIN);
        d.setTopMargin(MARGIN);
        d.setBottomMargin(MARGIN);
        d.setColumnWidth(WIDTH);
        d.setWhenNoDataType(net.sf.jasperreports.engine.type.WhenNoDataTypeEnum.ALL_SECTIONS_NO_DETAIL);

        JRDesignStyle base = new JRDesignStyle();
        base.setName("base");
        base.setDefault(true);
        base.setFontName(FONT);
        base.setFontSize(8f);
        base.setPdfEmbedded(true);
        base.setPdfEncoding("Identity-H");
        d.addStyle(base);

        int[] widths = widths(t.columns());
        for (int i = 0; i < t.columns().size(); i++) {
            JRDesignField f = new JRDesignField();
            f.setName("c" + i);
            f.setValueClass(String.class);
            d.addField(f);
        }

        // title: report name, filters
        JRDesignBand title = new JRDesignBand();
        title.setHeight(48);
        JRDesignStaticText name = text(t.title(), 0, 0, WIDTH, 22, 15f, true);
        name.setForecolor(BRAND);
        title.addElement(name);
        title.addElement(text(t.subtitle(), 0, 24, WIDTH, 22, 8f, false));
        d.setTitle(title);

        // column headers, repeated on every page
        JRDesignBand header = new JRDesignBand();
        header.setHeight(18);
        int x = 0;
        for (int i = 0; i < t.columns().size(); i++) {
            ReportTable.Column c = t.columns().get(i);
            JRDesignStaticText h = text(c.header(), x, 0, widths[i], 18, 8f, true);
            h.setMode(ModeEnum.OPAQUE);
            h.setBackcolor(HEADER_BG);
            h.setHorizontalTextAlign(c.isRightAligned() ? HorizontalTextAlignEnum.RIGHT : HorizontalTextAlignEnum.LEFT);
            pad(h);
            header.addElement(h);
            x += widths[i];
        }
        d.setColumnHeader(header);

        // one line per row; long text wraps
        JRDesignBand detail = new JRDesignBand();
        detail.setHeight(14);
        x = 0;
        for (int i = 0; i < t.columns().size(); i++) {
            ReportTable.Column c = t.columns().get(i);
            JRDesignTextField f = new JRDesignTextField();
            f.setX(x);
            f.setY(0);
            f.setWidth(widths[i]);
            f.setHeight(14);
            f.setExpression(new JRDesignExpression("$F{c" + i + "}"));
            f.setTextAdjust(TextAdjustEnum.STRETCH_HEIGHT);
            f.setStretchType(StretchTypeEnum.CONTAINER_HEIGHT);   // a wrapped cell makes the whole row taller
            f.setBlankWhenNull(true);
            f.setVerticalTextAlign(VerticalTextAlignEnum.MIDDLE);
            f.setHorizontalTextAlign(c.isRightAligned() ? HorizontalTextAlignEnum.RIGHT : HorizontalTextAlignEnum.LEFT);
            f.getLineBox().getBottomPen().setLineWidth(0.25f);
            f.getLineBox().getBottomPen().setLineColor(new Color(0xDE, 0xE2, 0xE6));
            pad(f);
            detail.addElement(f);
            x += widths[i];
        }
        ((JRDesignSection) d.getDetailSection()).addBand(detail);

        // totals and notes
        JRDesignBand summary = new JRDesignBand();
        int y = 4;
        if (t.totals() != null) {
            x = 0;
            for (int i = 0; i < t.columns().size(); i++) {
                Object v = t.totals().get(i);
                if (v != null) {
                    JRDesignStaticText s = text(cellText.format(v, i == 0 ? ReportTable.Type.TEXT : t.columns().get(i).type()),
                            x, y, widths[i], 16, 8f, true);
                    s.setHorizontalTextAlign(t.columns().get(i).isRightAligned() ? HorizontalTextAlignEnum.RIGHT : HorizontalTextAlignEnum.LEFT);
                    s.getLineBox().getTopPen().setLineWidth(0.75f);
                    pad(s);
                    summary.addElement(s);
                }
                x += widths[i];
            }
            y += 22;
        }
        List<String> notes = new ArrayList<>(t.notes());
        if (t.rows().isEmpty()) {
            notes.add(0, "No cheques match these filters.");
        }
        if (t.truncated()) {
            notes.add("Only the first " + t.size() + " rows are shown; narrow the filters to see the rest.");
        }
        for (String n : notes) {
            summary.addElement(text(n, 0, y, WIDTH, 14, 7.5f, false));
            y += 14;
        }
        summary.setHeight(y + 4);
        d.setSummary(summary);

        // page footer: who printed it and the page number
        JRDesignBand pageFooter = new JRDesignBand();
        pageFooter.setHeight(16);
        pageFooter.addElement(text(footer, 0, 2, WIDTH - 120, 14, 7f, false));
        JRDesignTextField page = new JRDesignTextField();
        page.setX(WIDTH - 120);
        page.setY(2);
        page.setWidth(120);
        page.setHeight(14);
        page.setFontSize(7f);
        page.setHorizontalTextAlign(HorizontalTextAlignEnum.RIGHT);
        page.setExpression(new JRDesignExpression("\"Page \" + $V{PAGE_NUMBER}"));
        pageFooter.addElement(page);
        d.setPageFooter(pageFooter);
        return d;
    }

    private static JRDesignStaticText text(String s, int x, int y, int w, int h, float size, boolean bold) {
        JRDesignStaticText t = new JRDesignStaticText();
        t.setText(s == null ? "" : s);
        t.setX(x);
        t.setY(y);
        t.setWidth(w);
        t.setHeight(h);
        t.setFontSize(size);
        t.setBold(bold);
        t.setVerticalTextAlign(VerticalTextAlignEnum.MIDDLE);
        return t;
    }

    private static void pad(JRDesignElement e) {
        if (e instanceof JRDesignStaticText s) {
            s.getLineBox().setLeftPadding(3);
            s.getLineBox().setRightPadding(3);
        } else if (e instanceof JRDesignTextField f) {
            f.getLineBox().setLeftPadding(3);
            f.getLineBox().setRightPadding(3);
        }
    }

    /** Column widths in points, in proportion to each column's share, filling the page width. */
    private static int[] widths(List<ReportTable.Column> columns) {
        int total = columns.stream().mapToInt(ReportTable.Column::width).sum();
        int[] w = new int[columns.size()];
        int used = 0;
        for (int i = 0; i < w.length; i++) {
            w[i] = i == w.length - 1 ? WIDTH - used : WIDTH * columns.get(i).width() / total;
            used += w[i];
        }
        return w;
    }
}
