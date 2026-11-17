package com.chequetrack.received.service;

import com.chequetrack.attachment.AttachmentProperties;
import com.chequetrack.attachment.domain.AttachmentCategory;
import com.chequetrack.attachment.service.AttachmentService;
import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.common.exception.NotFoundException;
import com.chequetrack.ocr.MicrLine;
import com.chequetrack.ocr.OcrService;
import com.chequetrack.received.domain.ReceivedCheque;
import com.chequetrack.setup.domain.BankBranch;
import com.chequetrack.setup.service.BankService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChequeScanService {

    private final OcrService ocrService;
    private final BankService bankService;
    private final AttachmentService attachmentService;
    private final ReceivedChequeService chequeService;
    private final AttachmentProperties attachmentProperties;
    private final ApplicationEventPublisher events;

    public ChequeScanService(OcrService ocrService, BankService bankService, AttachmentService attachmentService,
                             ReceivedChequeService chequeService, AttachmentProperties attachmentProperties,
                             ApplicationEventPublisher events) {
        this.ocrService = ocrService;
        this.bankService = bankService;
        this.attachmentService = attachmentService;
        this.chequeService = chequeService;
        this.attachmentProperties = attachmentProperties;
        this.events = events;
    }

    public boolean enabled() {
        return ocrService.enabled();
    }

    public record ScanResult(boolean found, String chequeNumber, String routingNumber, Long bankBranchId,
                             String bankBranchLabel, String accountNumber, String message, String rawText) {
    }

    public record CheckLine(String field, String recorded, String scanned, boolean matches) {
    }

    public ScanResult read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Choose a picture of the cheque.");
        }
        if (file.getSize() > attachmentProperties.maxSizeBytes()) {
            throw new BusinessException("The file is too large. The limit is "
                    + (attachmentProperties.maxSizeBytes() / (1024 * 1024)) + " MB.");
        }
        try {
            return read(file.getBytes());
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read the uploaded file", ex);
        }
    }

    /** Checks a saved cheque against one of its attached scans. Recorded in the audit log. */
    public List<CheckLine> check(Long chequeId, Long attachmentId) {
        ReceivedCheque c = chequeService.get(chequeId);
        AttachmentService.StoredFile stored = attachmentService.download(attachmentId);
        if (!ReceivedCheque.RECORD_TYPE.equals(stored.attachment().getOwnerType())
                || !chequeId.equals(stored.attachment().getOwnerId())) {
            throw new NotFoundException("Attachment", attachmentId);
        }
        ScanResult scan;
        try {
            scan = read(Files.readAllBytes(stored.path()));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read the attached file", ex);
        }
        if (!scan.found()) {
            throw new BusinessException(scan.message());
        }
        List<CheckLine> lines = new ArrayList<>();
        lines.add(new CheckLine("Cheque number", c.getChequeNumber(), scan.chequeNumber(),
                sameNumber(c.getChequeNumber(), scan.chequeNumber())));
        lines.add(new CheckLine("Routing number", c.getDrawerBankBranch().getRoutingNumber(), scan.routingNumber(),
                c.getDrawerBankBranch().getRoutingNumber().equals(scan.routingNumber())));
        lines.add(new CheckLine("Account number", c.getDrawerAccountNumber(), scan.accountNumber(),
                sameNumber(c.getDrawerAccountNumber(), scan.accountNumber())));

        String summary = lines.stream()
                .map(l -> l.field().toLowerCase() + (l.matches() ? " matches" : " differs (scan: " + nz(l.scanned()) + ")"))
                .collect(Collectors.joining(", "));
        events.publishEvent(AuditEvent.of("CHEQUE_SCAN_CHECKED", ReceivedCheque.RECORD_TYPE, chequeId,
                "Checked against scan '" + stored.attachment().getOriginalFilename() + "': " + summary));
        return lines;
    }

    private ScanResult read(byte[] bytes) {
        Map<String, BankBranch> byRouting = bankService.selectableBranches().stream()
                .collect(Collectors.toMap(BankBranch::getRoutingNumber, Function.identity(), (a, b) -> a));
        OcrService.OcrResult result = ocrService.readMicr(bytes, byRouting::containsKey);
        String raw = result.rawText() == null ? "" : result.rawText().strip();
        if (result.line().isEmpty()) {
            return new ScanResult(false, null, null, null, null, null,
                    "Couldn't find the MICR line (the printed numbers along the bottom). Please type the details, "
                            + "or try again with the whole cheque flat, sharp and well lit.", raw);
        }
        MicrLine m = result.line().get();
        BankBranch branch = m.routingKnown() ? byRouting.get(m.routingNumber()) : null;
        String message = branch != null
                ? "Read from the scan. Check the highlighted fields before saving."
                : "Read from the scan, but routing number " + m.routingNumber()
                + " is not in the bank list. Choose the bank branch yourself, or add it under Banks.";
        return new ScanResult(true, m.chequeNumber(), m.routingNumber(), branch == null ? null : branch.getId(),
                branch == null ? null : branch.getLabel(), m.accountNumber(), message, raw);
    }

    /** Numbers compare without leading zeros, spaces or dashes. */
    private static boolean sameNumber(String recorded, String scanned) {
        if (recorded == null || scanned == null) {
            return false;
        }
        return normalise(recorded).equals(normalise(scanned));
    }

    private static String normalise(String s) {
        String digits = s.replaceAll("[^0-9A-Za-z]", "");
        String stripped = digits.replaceFirst("^0+", "");
        return stripped.isEmpty() ? "0" : stripped;
    }

    private static String nz(String s) {
        return s == null ? "not read" : s;
    }
}
