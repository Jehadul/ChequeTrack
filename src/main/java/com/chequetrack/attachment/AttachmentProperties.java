package com.chequetrack.attachment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.attachments")
public record AttachmentProperties(@NotBlank String dir, @Min(1) long maxSizeBytes) {
}
