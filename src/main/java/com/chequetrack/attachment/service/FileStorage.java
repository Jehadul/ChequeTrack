package com.chequetrack.attachment.service;

import com.chequetrack.attachment.AttachmentProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Stores attachment files on the local disk, outside the web root. File names are
 * random UUIDs grouped by month ({@code 2026/10/<uuid>.pdf}); the user's file name
 * is never part of the path, so it cannot be used to write elsewhere.
 */
@Component
public class FileStorage {

    private final Path root;

    public FileStorage(AttachmentProperties properties) {
        this.root = Path.of(properties.dir()).toAbsolutePath().normalize();
    }

    /** Copies the content to a new file and returns its storage key. */
    public String store(InputStream content, String extension, LocalDate today) {
        String key = String.format("%04d/%02d/%s.%s", today.getYear(), today.getMonthValue(), UUID.randomUUID(), extension);
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store attachment", ex);
        }
        return key;
    }

    public Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }

    /** Removes a file written by {@link #store} when the database part of an upload fails. */
    public void discard(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ignored) {
            // best effort: an orphan file is harmless
        }
    }
}
