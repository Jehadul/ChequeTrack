package com.chequetrack.attachment.service;

import com.chequetrack.attachment.AttachmentProperties;
import com.chequetrack.attachment.domain.Attachment;
import com.chequetrack.attachment.domain.AttachmentCategory;
import com.chequetrack.attachment.repository.AttachmentRepository;
import com.chequetrack.attachment.service.AttachmentOwnerResolver.AttachmentOwner;
import com.chequetrack.common.event.AuditEvent;
import com.chequetrack.common.exception.BusinessException;
import com.chequetrack.common.exception.NotFoundException;
import com.chequetrack.security.service.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Uploading, listing, downloading and removing attachments. Every operation first
 * resolves the owner record, so the owner's own access rules apply.
 */
@Service
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final FileStorage storage;
    private final AttachmentProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Map<String, AttachmentOwnerResolver> resolvers;

    public AttachmentService(AttachmentRepository attachmentRepository,
                             FileStorage storage,
                             AttachmentProperties properties,
                             ApplicationEventPublisher events,
                             Clock clock,
                             List<AttachmentOwnerResolver> resolvers) {
        this.attachmentRepository = attachmentRepository;
        this.storage = storage;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
        this.resolvers = resolvers.stream()
                .collect(Collectors.toMap(AttachmentOwnerResolver::ownerType, Function.identity()));
    }

    /** The owner record, or 404 when the type is unknown or the user may not see the record. */
    @Transactional(readOnly = true)
    public AttachmentOwner owner(String ownerType, Long ownerId) {
        AttachmentOwnerResolver resolver = resolvers.get(ownerType);
        if (resolver == null) {
            throw new NotFoundException("Record type", ownerType);
        }
        return resolver.resolve(ownerId);
    }

    @Transactional(readOnly = true)
    public List<Attachment> list(String ownerType, Long ownerId) {
        AttachmentOwner owner = owner(ownerType, ownerId);
        return attachmentRepository.findByCompanyIdAndOwnerTypeAndOwnerIdOrderByCreatedAtDesc(owner.companyId(), ownerType, ownerId);
    }

    @Transactional
    public Attachment upload(String ownerType, Long ownerId, AttachmentCategory category, String description,
                             MultipartFile file) {
        AttachmentOwner owner = owner(ownerType, ownerId);
        if (!owner.canModify()) {
            throw new BusinessException("You cannot add files to this record.");
        }
        if (category == null) {
            throw new BusinessException("Choose what kind of document this is.");
        }
        if (description != null && description.length() > 300) {
            throw new BusinessException("The description must be at most 300 characters.");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Choose a file to upload.");
        }
        if (file.getSize() > properties.maxSizeBytes()) {
            throw new BusinessException("The file is too large. The limit is " + (properties.maxSizeBytes() / (1024 * 1024)) + " MB.");
        }
        FileTypeDetector.FileType type = detectType(file);
        String originalName = safeFilename(file.getOriginalFilename(), type.extension());

        String key;
        String sha256;
        try (InputStream in = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            key = storage.store(new DigestInputStream(in, digest), type.extension(), LocalDate.now(clock));
            sha256 = HexFormat.of().formatHex(digest.digest());
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read the uploaded file", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }

        try {
            Attachment attachment = Attachment.create(owner.companyId(), owner.branchId(), ownerType, ownerId, category,
                    originalName, type.mimeType(), file.getSize(), sha256, key, description);
            attachmentRepository.saveAndFlush(attachment);
            events.publishEvent(AuditEvent.of("ATTACHMENT_ADDED", ownerType, ownerId,
                    "Attached " + category.getLabel().toLowerCase() + " '" + originalName + "' (" + attachment.getSizeLabel() + ")"));
            return attachment;
        } catch (RuntimeException ex) {
            storage.discard(key);
            throw ex;
        }
    }

    /** The attachment and its file, after checking the user may see the owner record. */
    @Transactional(readOnly = true)
    public StoredFile download(Long attachmentId) {
        Attachment attachment = get(attachmentId);
        owner(attachment.getOwnerType(), attachment.getOwnerId());
        Path path = storage.resolve(attachment.getStorageKey());
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("File", attachmentId);
        }
        return new StoredFile(attachment, path);
    }

    /** Removes an attachment from the record. The file stays on disk (nothing is permanently deleted). */
    @Transactional
    public Attachment remove(Long attachmentId) {
        Attachment attachment = get(attachmentId);
        AttachmentOwner owner = owner(attachment.getOwnerType(), attachment.getOwnerId());
        if (!owner.canModify()) {
            throw new BusinessException("You cannot remove files from this record.");
        }
        attachment.markDeleted();
        events.publishEvent(AuditEvent.of("ATTACHMENT_REMOVED", attachment.getOwnerType(), attachment.getOwnerId(),
                "Removed " + attachment.getCategory().getLabel().toLowerCase() + " '" + attachment.getOriginalFilename() + "'"));
        return attachment;
    }

    private Attachment get(Long id) {
        return attachmentRepository.findByIdAndCompanyId(id, CurrentUser.get().getCompanyId())
                .orElseThrow(() -> new NotFoundException("Attachment", id));
    }

    private static FileTypeDetector.FileType detectType(MultipartFile file) {
        byte[] header = new byte[FileTypeDetector.HEADER_LENGTH];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(header, 0, header.length);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read the uploaded file", ex);
        }
        byte[] actual = read == header.length ? header : Arrays.copyOf(header, read);
        return FileTypeDetector.detect(actual)
                .orElseThrow(() -> new BusinessException("Only PDF, JPEG and PNG files are allowed."));
    }

    /** File name for display and download: no folders, no control characters, correct extension. */
    static String safeFilename(String original, String extension) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\"<>|:*?]", "_").trim();
        if (name.isEmpty() || name.startsWith(".")) {
            name = "file";
        }
        if (name.length() > 200) {
            name = name.substring(0, 200);
        }
        String lower = name.toLowerCase();
        boolean extensionMatches = lower.endsWith("." + extension) || (extension.equals("jpg") && lower.endsWith(".jpeg"));
        return extensionMatches ? name : name + "." + extension;
    }

    /** An attachment with the path of its file on disk. */
    public record StoredFile(Attachment attachment, Path path) {
    }
}
