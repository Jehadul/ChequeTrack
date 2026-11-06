package com.chequetrack.notification;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record NotificationProperties(@NotBlank String baseUrl, Mail mail, Reminders reminders) {

    public record Mail(boolean enabled, @NotBlank String from) {
    }

    public record Reminders(@NotBlank String cron, @Min(1) int unclearedDays, @Min(1) int approvalPendingHours) {
    }

    /** Absolute link for emails, e.g. link("/received/5"). */
    public String link(String path) {
        return baseUrl.replaceAll("/+$", "") + path;
    }
}
