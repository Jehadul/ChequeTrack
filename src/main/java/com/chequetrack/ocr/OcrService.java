package com.chequetrack.ocr;

import com.chequetrack.attachment.service.FileTypeDetector;
import com.chequetrack.common.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    /** Bigger pictures are refused before decoding (a small PNG can expand to a huge bitmap). */
    private static final long MAX_PIXELS = 60_000_000L;
    /** Tesseract reads MICR digits best around this width. */
    private static final int TARGET_WIDTH = 2400;
    /** Wider pictures are scaled down first: no gain in accuracy, only time. */
    private static final int MAX_WIDTH = 4000;
    /** Share of the cheque height, from the bottom, that holds the MICR line. */
    private static final double MICR_BAND = 0.30;

    private final OcrProperties properties;
    private final Semaphore slots;
    private final String command;

    public OcrService(OcrProperties properties) {
        this.properties = properties;
        this.slots = new Semaphore(properties.maxConcurrent());
        this.command = findCommand(properties.command());
        if (properties.enabled()) {
            log.info("Cheque scan OCR is on, using {}", command);
        }
    }

    /**
     * The configured command; when it is the bare name "tesseract" and the program is not on the PATH,
     * the usual Windows install folders are tried (the installer does not add itself to the PATH).
     */
    private static String findCommand(String configured) {
        if (!"tesseract".equals(configured)) {
            return configured;
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                if (!dir.isBlank() && (Files.isRegularFile(Path.of(dir, "tesseract"))
                        || Files.isRegularFile(Path.of(dir, "tesseract.exe")))) {
                    return configured;
                }
            }
        }
        List<String> bases = new ArrayList<>();
        for (String env : List.of("LOCALAPPDATA", "ProgramFiles", "ProgramFiles(x86)")) {
            String base = System.getenv(env);
            if (base != null && !base.isBlank()) {
                bases.add(base);
            }
        }
        for (String base : bases) {
            Path exe = Path.of(base, "Tesseract-OCR", "tesseract.exe");
            if (Files.isRegularFile(exe)) {
                return exe.toString();
            }
        }
        return configured;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public record OcrResult(Optional<MicrLine> line, String rawText) {
    }

    /**
     * Reads the MICR line of a cheque picture (JPEG, PNG or the first page of a PDF). Tries the bottom band
     * as it is, then enlarged, then the whole picture, upright and turned; stops at the first read whose
     * routing number is a known bank branch. (Tesseract sometimes drops a repeated digit at one size and
     * not at another, so a second size is worth the extra second.)
     */
    public OcrResult readMicr(byte[] file, Predicate<String> knownRouting) {
        if (!properties.enabled()) {
            throw new BusinessException("Reading cheques from a scan is switched off.");
        }
        BufferedImage picture = load(file);
        boolean acquired = false;
        Path work = null;
        try {
            acquired = slots.tryAcquire(properties.timeoutSeconds(), TimeUnit.SECONDS);
            if (!acquired) {
                throw new BusinessException("The scanner is busy. Try again in a moment.");
            }
            work = Files.createTempDirectory("chequetrack-ocr");
            OcrResult fallback = null;
            int n = 0;
            for (BufferedImage turned : orientations(picture)) {
                BufferedImage band = bottomBand(turned);
                List<Attempt> attempts = List.of(
                        new Attempt(gray(band, 1.0), "6"),
                        new Attempt(enlarged(band), "6"),
                        new Attempt(gray(turned, 1.0), "11"));
                for (Attempt a : attempts) {
                    String text = tesseract(a.image(), a.psm(), work, n++);
                    Optional<MicrLine> line = MicrParser.parse(text, knownRouting);
                    if (line.isPresent() && line.get().routingKnown()) {
                        return new OcrResult(line, text);
                    }
                    if (fallback == null || fallback.line().isEmpty() && line.isPresent()) {
                        fallback = new OcrResult(line, text);
                    }
                }
            }
            return fallback;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Reading the scan was interrupted.");
        } catch (IOException ex) {
            log.warn("OCR failed: {}", ex.getMessage());
            throw new BusinessException("Couldn't read the scan. Please type the details.");
        } finally {
            if (acquired) {
                slots.release();
            }
            if (work != null) {
                try {
                    FileSystemUtils.deleteRecursively(work);
                } catch (IOException ex) {
                    log.debug("Could not delete OCR temp folder {}", work);
                }
            }
        }
    }

    private record Attempt(BufferedImage image, String psm) {
    }

    private String tesseract(BufferedImage image, String psm, Path work, int n) throws IOException, InterruptedException {
        Path in = work.resolve("in" + n + ".png");
        Path out = work.resolve("out" + n + ".txt");
        Path err = work.resolve("err" + n + ".txt");
        ImageIO.write(image, "png", in.toFile());

        List<String> cmd = new ArrayList<>(Arrays.asList(command, in.toString(), "stdout",
                "-l", properties.language(), "--psm", psm));
        if (properties.tessdataDir() != null && !properties.tessdataDir().isBlank()) {
            cmd.add("--tessdata-dir");
            cmd.add(properties.tessdataDir());
        }
        if (properties.language().startsWith("eng")) {
            // the general English model: only digits are wanted from the MICR line. The space must be
            // in the list too, or the groups (cheque no, routing, account, code) come out run together.
            cmd.add("-c");
            cmd.add("tessedit_char_whitelist=0123456789 ");
            cmd.add("-c");
            cmd.add("preserve_interword_spaces=1");
        }
        Process p;
        try {
            p = new ProcessBuilder(cmd).redirectOutput(out.toFile()).redirectError(err.toFile()).start();
        } catch (IOException ex) {
            log.warn("Tesseract could not be started with '{}': {}", command, ex.getMessage());
            throw new BusinessException("Reading scans is not set up on the server (Tesseract was not found).");
        }
        if (!p.waitFor(properties.timeoutSeconds(), TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new BusinessException("Reading the scan took too long. Please type the details.");
        }
        if (p.exitValue() != 0) {
            log.warn("Tesseract exited with {}: {}", p.exitValue(), Files.readString(err, StandardCharsets.UTF_8).strip());
            throw new BusinessException("Couldn't read the scan. Please type the details.");
        }
        return Files.readString(out, StandardCharsets.UTF_8);
    }

    private static BufferedImage load(byte[] file) {
        byte[] header = Arrays.copyOf(file, Math.min(file.length, FileTypeDetector.HEADER_LENGTH));
        FileTypeDetector.FileType type = FileTypeDetector.detect(header)
                .orElseThrow(() -> new BusinessException("Only PDF, JPEG and PNG files are allowed."));
        try {
            if ("pdf".equals(type.extension())) {
                try (PDDocument doc = Loader.loadPDF(file)) {
                    if (doc.getNumberOfPages() == 0) {
                        throw new BusinessException("The PDF has no pages.");
                    }
                    return new PDFRenderer(doc).renderImageWithDPI(0, 300, ImageType.GRAY);
                }
            }
            try (var in = ImageIO.createImageInputStream(new ByteArrayInputStream(file))) {
                var readers = ImageIO.getImageReaders(in);
                if (!readers.hasNext()) {
                    throw new BusinessException("The picture could not be opened.");
                }
                var reader = readers.next();
                try {
                    reader.setInput(in);
                    if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                        throw new BusinessException("The picture is too large. Use a smaller photo.");
                    }
                    return reader.read(0);
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException ex) {
            throw new BusinessException("The file could not be opened as a picture.");
        }
    }

    /** A cheque is wider than tall: a portrait picture is tried turned both ways, a landscape one also upside down. */
    private static List<BufferedImage> orientations(BufferedImage img) {
        if (img.getHeight() > img.getWidth()) {
            return List.of(rotate(img, 90), rotate(img, 270));
        }
        return List.of(img, rotate(img, 180));
    }

    private static BufferedImage bottomBand(BufferedImage img) {
        int h = (int) Math.round(img.getHeight() * MICR_BAND);
        return img.getSubimage(0, img.getHeight() - h, img.getWidth(), h);
    }

    /** At least 1.5 times bigger, up to about TARGET_WIDTH wide (small digits read better enlarged). */
    private static BufferedImage enlarged(BufferedImage src) {
        return gray(src, Math.max(1.5, Math.min(3.0, (double) TARGET_WIDTH / src.getWidth())));
    }

    /** Grayscale at the given scale, never wider than MAX_WIDTH. Tesseract does its own thresholding. */
    private static BufferedImage gray(BufferedImage src, double requested) {
        double scale = Math.min(requested, (double) MAX_WIDTH / src.getWidth());
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static BufferedImage rotate(BufferedImage src, int degrees) {
        boolean quarter = degrees == 90 || degrees == 270;
        int w = quarter ? src.getHeight() : src.getWidth();
        int h = quarter ? src.getWidth() : src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        AffineTransform t = new AffineTransform();
        t.translate(w / 2.0, h / 2.0);
        t.rotate(Math.toRadians(degrees));
        t.translate(-src.getWidth() / 2.0, -src.getHeight() / 2.0);
        g.drawImage(src, t, null);
        g.dispose();
        return out;
    }
}
