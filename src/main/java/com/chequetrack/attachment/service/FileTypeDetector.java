package com.chequetrack.attachment.service;

import java.util.Arrays;
import java.util.Optional;

/**
 * Recognises the allowed file types from their first bytes ("magic numbers"),
 * ignoring the file name and the browser's declared type, which can be faked.
 */
public final class FileTypeDetector {

    /** An allowed type: MIME type and the extension used for the stored file. */
    public record FileType(String mimeType, String extension) {
    }

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    /** Bytes needed to recognise every allowed type. */
    public static final int HEADER_LENGTH = 8;

    private FileTypeDetector() {
    }

    public static Optional<FileType> detect(byte[] header) {
        if (startsWith(header, PDF)) {
            return Optional.of(new FileType("application/pdf", "pdf"));
        }
        if (startsWith(header, PNG)) {
            return Optional.of(new FileType("image/png", "png"));
        }
        if (startsWith(header, JPEG)) {
            return Optional.of(new FileType("image/jpeg", "jpg"));
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
    }
}
