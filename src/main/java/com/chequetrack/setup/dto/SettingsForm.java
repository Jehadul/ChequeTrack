package com.chequetrack.setup.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class SettingsForm {

    private Long version;

    @Min(value = 1, message = "At least 1 day.")
    @Max(value = 365, message = "At most 365 days.")
    private int chequeValidityDays;

    @Min(0) @Max(90)
    private int nearExpiryDays;

    @Min(0) @Max(30)
    private int reminderLeadDays;

    @Min(0) @Max(12)
    private int maxDefers;

    @Min(0) @Max(500)
    private int lowStockLeaves;

    @Min(1) @Max(180)
    private int bounceNoticeDays;

    @AssertTrue(message = "The expiry warning must start before the cheque expires.")
    public boolean isWarningBeforeExpiry() {
        return nearExpiryDays < chequeValidityDays;
    }
}
