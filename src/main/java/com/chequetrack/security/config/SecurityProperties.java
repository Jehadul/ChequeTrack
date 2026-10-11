package com.chequetrack.security.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** All tunable security values, bound from {@code app.security.*}. */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(@Valid Password password,
                                 @Valid Lockout lockout,
                                 @Valid Jwt jwt,
                                 @Valid Bootstrap bootstrap) {

    /** Password rules. {@code expiryDays = 0} turns expiry off; {@code historyCount = 0} allows re-use. */
    public record Password(@Min(8) int minLength,
                           boolean requireUppercase,
                           boolean requireLowercase,
                           boolean requireDigit,
                           boolean requireSpecial,
                           @Min(0) int historyCount,
                           @Min(0) int expiryDays) {
    }

    public record Lockout(@Min(1) int maxFailedAttempts, @Min(1) int lockMinutes) {
    }

    /** {@code secret} is a Base64 key of at least 32 bytes (HS256). */
    public record Jwt(@NotBlank String secret, @NotBlank String issuer, @Min(1) int expiryMinutes) {
    }

    /** First administrator created when the user table is empty. Blank password = generate one. */
    public record Bootstrap(@NotBlank String adminUsername, String adminPassword) {
    }
}
