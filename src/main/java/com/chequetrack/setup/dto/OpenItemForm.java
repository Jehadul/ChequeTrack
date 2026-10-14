package com.chequetrack.setup.dto;

import com.chequetrack.setup.domain.OpenItemType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class OpenItemForm {

    private Long id;
    private Long version;

    /** Set by the controller from the URL (/setup/invoices or /setup/bills), not by the user. */
    private OpenItemType itemType;

    private Long branchId;

    @NotNull(message = "Choose the customer or supplier.")
    private Long partyId;

    @NotBlank(message = "Enter the number.")
    @Pattern(regexp = "[A-Za-z0-9/_-]{1,30}", message = "Use up to 30 letters, digits, /, - or _.")
    private String number;

    @NotNull(message = "Enter the date.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate itemDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dueDate;

    @NotNull(message = "Enter the amount.")
    @DecimalMin(value = "0.01", message = "The amount must be more than zero.")
    @Digits(integer = 16, fraction = 2, message = "Up to 2 decimal places.")
    private BigDecimal amount;

    @Size(max = 300)
    private String narration;

    @AssertTrue(message = "The due date cannot be before the date.")
    public boolean isDueDateValid() {
        return dueDate == null || itemDate == null || !dueDate.isBefore(itemDate);
    }
}
