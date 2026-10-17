package com.chequetrack.attachment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.attachments.*}: where files are stored and how big they may be.
 *
 * @param dir          storage folder, outside the web root (relative paths are resolved
 *                     against the working directory)
 * @param maxSizeBytes largest accepted file
 */
@Validated
@ConfigurationProperties(prefix = "app.attachments")
public record AttachmentProperties(@NotBlank String dir, @Min(1) long maxSizeBytes) {
}
