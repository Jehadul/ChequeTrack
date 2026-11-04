package com.chequetrack.printing.service;

import com.chequetrack.common.domain.AmountInWords;
import com.chequetrack.common.web.DisplayFormat;
import com.chequetrack.printing.domain.ChequeLayout;
import com.chequetrack.printing.domain.FieldCode;
import com.chequetrack.printing.domain.FontFamily;
import com.chequetrack.printing.domain.LayoutField;
import com.chequetrack.printing.domain.PrinterProfile;
import com.chequetrack.printing.domain.TextAlign;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ChequePdfRenderer {

    private static final float PT_PER_MM = 72f / 25.4f;
    private static final double MIN_SIZE_PT = 6;
    private static final String CROSSING_TEXT = "A/C Payee Only";

    private final DisplayFormat displayFormat;

    public ChequePdfRenderer(DisplayFormat displayFormat) {
        this.displayFormat = displayFormat;
    }

    /** What is written on one cheque. */
    public record ChequeData(LocalDate date, String payee, BigDecimal amount, boolean accountPayee) {
    }

    /** {@code grid}: alignment grid and field outlines for a test print on plain paper. */
    public record Options(boolean grid, String footer) {
        public static final Options PRINT = new Options(false, null);
    }

    /** The words each field prints for a cheque: used by the PDF and by the on-screen designer. */
    public Map<FieldCode, String> texts(ChequeLayout layout, ChequeData data) {
        Map<FieldCode, String> t = new EnumMap<>(FieldCode.class);
        t.put(FieldCode.DATE, layout.getDateFormat().format(data.date()));
        t.put(FieldCode.PAYEE, data.payee());
        t.put(FieldCode.AMOUNT_WORDS, AmountInWords.of(data.amount(), layout.getWordsLanguage()));
        String figures = displayFormat.amount(data.amount());
        t.put(FieldCode.AMOUNT_FIGURES, layout.isProtectionStars() ? "**" + figures + "**" : figures);
        t.put(FieldCode.CROSSING, data.accountPayee() ? CROSSING_TEXT : "");
        String company = layout.getSignatoryCompany() == null ? "" : layout.getSignatoryCompany();
        String names = layout.getSignatoryNames() == null ? "" : layout.getSignatoryNames();
        t.put(FieldCode.SIGNATORY, (company + "\n" + names).strip());
        return t;
    }

    public byte[] render(ChequeLayout layout, List<ChequeData> cheques, PrinterProfile printer, Options options) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Fonts fonts = new Fonts(doc);
            PDRectangle size = new PDRectangle(mm(layout.getWidthMm()), mm(layout.getHeightMm()));
            for (ChequeData data : cheques) {
                PDPage page = new PDPage(size);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    if (printer != null) {
                        cs.transform(Matrix.getTranslateInstance(mm(printer.getOffsetXMm()), -mm(printer.getOffsetYMm())));
                    }
                    Page p = new Page(cs, fonts, mm(layout.getHeightMm()));
                    if (options.grid()) {
                        p.grid(layout);
                    }
                    Map<FieldCode, String> texts = texts(layout, data);
                    for (LayoutField f : layout.getFields()) {
                        if (!f.isPrinted()) {
                            continue;
                        }
                        if (options.grid()) {
                            p.outline(f);
                        }
                        p.field(f, texts.get(f.getFieldCode()));
                    }
                    if (options.footer() != null) {
                        p.footer(options.footer());
                    }
                }
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not produce the cheque PDF", ex);
        }
    }

    static float mm(double mm) {
        return (float) (mm * PT_PER_MM);
    }

    private static double ptToMm(double pt) {
        return pt / PT_PER_MM;
    }

    // ---------------------------------------------------------------------------------------------

    /** Fonts of one document; the Bangla font is embedded (as a subset) only when used. */
    private static final class Fonts {
        private final PDDocument doc;
        private PDFont bangla;
        private PDFont banglaBold;

        Fonts(PDDocument doc) {
            this.doc = doc;
        }

        PDFont latin(FontFamily family, boolean bold) {
            Standard14Fonts.FontName name = switch (family) {
                case TIMES -> bold ? Standard14Fonts.FontName.TIMES_BOLD : Standard14Fonts.FontName.TIMES_ROMAN;
                case COURIER -> bold ? Standard14Fonts.FontName.COURIER_BOLD : Standard14Fonts.FontName.COURIER;
                case HELVETICA -> bold ? Standard14Fonts.FontName.HELVETICA_BOLD : Standard14Fonts.FontName.HELVETICA;
            };
            return new PDType1Font(name);
        }

        PDFont bangla(boolean bold) throws IOException {
            if (bold) {
                if (banglaBold == null) {
                    banglaBold = load("fonts/HindSiliguri-SemiBold.ttf");
                }
                return banglaBold;
            }
            if (bangla == null) {
                bangla = load("fonts/HindSiliguri-Regular.ttf");
            }
            return bangla;
        }

        /** The field's font, or the Bangla font when the text has letters the standard font lacks. */
        PDFont forText(LayoutField f, String text) throws IOException {
            PDFont latin = latin(f.getFontFamily(), f.isBold());
            try {
                latin.encode(text);
                return latin;
            } catch (IllegalArgumentException ex) {
                return bangla(f.isBold());
            }
        }

        private PDFont load(String resource) throws IOException {
            try (InputStream in = new ClassPathResource(resource).getInputStream()) {
                return PDType0Font.load(doc, in, true);
            }
        }
    }

    /** Drawing on one page, with positions in mm from the top-left corner. */
    private static final class Page {
        private final PDPageContentStream cs;
        private final Fonts fonts;
        private final float pageHeight;

        Page(PDPageContentStream cs, Fonts fonts, float pageHeight) {
            this.cs = cs;
            this.fonts = fonts;
            this.pageHeight = pageHeight;
        }

        void field(LayoutField f, String text) throws IOException {
            if (text == null || text.isBlank()) {
                return;
            }
            switch (f.getFieldCode()) {
                case DATE -> date(f, text);
                case PAYEE, AMOUNT_FIGURES -> singleLine(f, text);
                case AMOUNT_WORDS -> words(f, text);
                case CROSSING -> crossing(f, text);
                case SIGNATORY -> signatory(f, text);
            }
        }

        private void date(LayoutField f, String text) throws IOException {
            PDFont font = fonts.forText(f, text);
            double size = f.getFontSizePt();
            if (f.getCharSpacingMm() <= 0) {
                write(font, size, baseline(f.getYMm(), size), aligned(f, font, size, text, f.getXMm(), f.getWidthMm()), text);
                return;
            }
            double pitch = f.getCharSpacingMm();
            for (int i = 0; i < text.length(); i++) {
                String ch = String.valueOf(text.charAt(i));
                double w = width(font, size, ch);
                write(font, size, baseline(f.getYMm(), size), f.getXMm() + i * pitch + (pitch - w) / 2, ch);
            }
        }

        private void singleLine(LayoutField f, String text) throws IOException {
            PDFont font = fonts.forText(f, text);
            double size = f.getFontSizePt();
            while (width(font, size, text) > f.getWidthMm() && size > MIN_SIZE_PT) {
                size -= 0.5;
            }
            write(font, size, baseline(f.getYMm(), f.getFontSizePt()), aligned(f, font, size, text, f.getXMm(), f.getWidthMm()), text);
        }

        private void words(LayoutField f, String text) throws IOException {
            PDFont font = fonts.forText(f, text);
            double x1 = f.getXMm();
            double x2 = f.getLine2XMm() == null ? x1 : f.getLine2XMm();
            double w1 = f.getWidthMm();
            double w2 = x1 + w1 - x2;
            double size = f.getFontSizePt();
            List<String> lines = wrap(font, size, text, w1, w2);
            while (lines.size() > 2 && size > 7) {
                size -= 0.5;
                lines = wrap(font, size, text, w1, w2);
            }
            double gap = f.getLineGapMm() == null ? ptToMm(f.getFontSizePt()) * 1.6 : f.getLineGapMm();
            double y = baseline(f.getYMm(), f.getFontSizePt());
            write(font, size, y, x1, lines.get(0));
            if (lines.size() > 1) {
                write(font, size, y + gap, x2, String.join(" ", lines.subList(1, lines.size())));
            }
        }

        private void crossing(LayoutField f, String text) throws IOException {
            PDFont font = fonts.forText(f, text);
            double size = f.getFontSizePt();
            double sizeMm = ptToMm(size);
            double baseY = baseline(f.getYMm(), size);
            double textX = (f.getWidthMm() - width(font, size, text)) / 2;
            cs.saveGraphicsState();
            // rotate around the field's left end on the baseline; local units are mm-scaled points
            cs.transform(Matrix.getRotateInstance(Math.toRadians(f.getAngleDeg()), mm(f.getXMm()), pageHeight - mm(baseY)));
            cs.setLineWidth(0.6f);
            float above = mm(sizeMm * 0.9 + 0.8);
            float below = -mm(1.2);
            cs.moveTo(0, above);
            cs.lineTo(mm(f.getWidthMm()), above);
            cs.moveTo(0, below);
            cs.lineTo(mm(f.getWidthMm()), below);
            cs.stroke();
            cs.beginText();
            cs.setFont(font, (float) size);
            cs.newLineAtOffset(mm(Math.max(textX, 0)), 0);
            cs.showText(text);
            cs.endText();
            cs.restoreGraphicsState();
        }

        private void signatory(LayoutField f, String text) throws IOException {
            String[] lines = text.split("\n");
            double size = f.getFontSizePt();
            double gap = f.getLineGapMm() == null ? ptToMm(size) * 1.5 : f.getLineGapMm();
            double y = baseline(f.getYMm(), size);
            for (String line : lines) {
                if (!line.isBlank()) {
                    PDFont font = fonts.forText(f, line);
                    write(font, size, y, aligned(f, font, size, line, f.getXMm(), f.getWidthMm()), line);
                }
                y += gap;
            }
        }

        // ---- test print ---------------------------------------------------------------------------

        void grid(ChequeLayout layout) throws IOException {
            double w = layout.getWidthMm();
            double h = layout.getHeightMm();
            PDFont label = fonts.latin(FontFamily.HELVETICA, false);
            cs.setLineWidth(0.2f);
            for (int x = 0; x <= w; x += 5) {
                cs.setStrokingColor(x % 10 == 0 ? 0.6f : 0.85f);
                line(x, 0, x, h);
            }
            for (int y = 0; y <= h; y += 5) {
                cs.setStrokingColor(y % 10 == 0 ? 0.6f : 0.85f);
                line(0, y, w, y);
            }
            cs.setNonStrokingColor(0.4f);
            for (int x = 10; x < w; x += 10) {
                write(label, 5, 2.5, x + 0.4, String.valueOf(x));
            }
            for (int y = 10; y < h; y += 10) {
                write(label, 5, y - 0.4, 0.6, String.valueOf(y));
            }
            cs.setStrokingColor(0f);
            cs.setLineWidth(0.5f);
            cs.addRect(0, 0, mm(w), mm(h));
            cs.stroke();
            cs.setNonStrokingColor(0f);
        }

        void outline(LayoutField f) throws IOException {
            double sizeMm = ptToMm(f.getFontSizePt());
            cs.saveGraphicsState();
            if (f.getAngleDeg() != 0) {
                // turn the box with its text: rotate about the same point as crossing()
                float px = mm(f.getXMm());
                float py = pageHeight - mm(baseline(f.getYMm(), f.getFontSizePt()));
                cs.transform(Matrix.getRotateInstance(Math.toRadians(f.getAngleDeg()), px, py));
                cs.transform(Matrix.getTranslateInstance(-px, -py));
            }
            cs.setStrokingColor(0.2f, 0.4f, 0.8f);
            cs.setLineWidth(0.3f);
            cs.addRect(mm(f.getXMm()), pageHeight - mm(f.getYMm() + sizeMm * 1.2), mm(f.getWidthMm()), mm(sizeMm * 1.2));
            cs.stroke();
            cs.restoreGraphicsState();
        }

        void footer(String text) throws IOException {
            cs.setNonStrokingColor(0.35f);
            PDFont font = fonts.latin(FontFamily.HELVETICA, false);
            String safe;
            try {
                font.encode(text);
                safe = text;
            } catch (IllegalArgumentException ex) {
                safe = text.replaceAll("[^\\x20-\\x7E]", "?");
            }
            write(font, 6, pageHeightMm() - 1.5, 2, safe);
            cs.setNonStrokingColor(0f);
        }

        // ---- helpers -------------------------------------------------------------------------------

        private double pageHeightMm() {
            return ptToMm(pageHeight);
        }

        /** Baseline of text in a box whose top edge is at y (mm), so letters sit inside the box. */
        private static double baseline(double yTop, double sizePt) {
            return yTop + ptToMm(sizePt) * 0.85;
        }

        private static double width(PDFont font, double sizePt, String text) throws IOException {
            return ptToMm(font.getStringWidth(text) / 1000 * sizePt);
        }

        private static double aligned(LayoutField f, PDFont font, double size, String text, double x, double width)
                throws IOException {
            TextAlign align = f.getAlign();
            double w = width(font, size, text);
            return switch (align) {
                case LEFT -> x;
                case CENTER -> x + (width - w) / 2;
                case RIGHT -> x + width - w;
            };
        }

        private static List<String> wrap(PDFont font, double size, String text, double w1, double w2) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                double limit = lines.isEmpty() ? w1 : w2;
                if (!line.isEmpty() && width(font, size, candidate) > limit) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            lines.add(line.toString());
            return lines;
        }

        private void write(PDFont font, double sizePt, double baselineMm, double xMm, String text)
                throws IOException {
            cs.beginText();
            cs.setFont(font, (float) sizePt);
            cs.newLineAtOffset(mm(xMm), pageHeight - mm(baselineMm));
            cs.showText(text);
            cs.endText();
        }

        private void line(double x1, double y1, double x2, double y2) throws IOException {
            cs.moveTo(mm(x1), pageHeight - mm(y1));
            cs.lineTo(mm(x2), pageHeight - mm(y2));
            cs.stroke();
        }
    }
}
