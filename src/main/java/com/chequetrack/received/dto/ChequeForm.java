package com.chequetrack.received.dto;

import com.chequetrack.received.domain.ChequeType;
import jakarta.validation.Valid;
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
import java.util.ArrayList;
import java.util.List;

@Data
public class ChequeForm {

    private Long branchId;

    @NotNull(message = "Choose the customer.")
    private Long partyId;

    @NotNull(message = "Choose the cheque type.")
    private ChequeType chequeType = ChequeType.CURRENT;

    @NotBlank(message = "Enter the cheque number.")
    @Pattern(regexp = "[0-9A-Za-z-]{1,20}", message = "Use up to 20 digits, letters or -.")
    private String chequeNumber;

    @NotNull(message = "Enter the date written on the cheque.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate chequeDate;

    @NotNull(message = "Enter the amount.")
    @DecimalMin(value = "0.01", message = "The amount must be more than zero.")
    @Digits(integer = 16, fraction = 2, message = "Up to 2 decimal places.")
    private BigDecimal amount;

    @NotNull(message = "Choose the drawer's bank branch.")
    private Long drawerBankBranchId;

    @NotBlank(message = "Enter the drawer's account number.")
    @Pattern(regexp = "[0-9A-Za-z-]{4,30}", message = "Use 4-30 digits, letters or -.")
    private String drawerAccountNumber;

    @Size(max = 150)
    private String drawerName;

    @NotNull(message = "Enter the date the cheque was received.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate receivedDate;

    @Size(max = 300)
    private String narration;

    /** Set when this cheque replaces a bounced or returned one. */
    private Long replacesId;

    /** Open invoices of the customer with how much of each this cheque pays (blank = not linked). */
    @Valid
    private List<Allocation> allocations = new ArrayList<>();

    @Data
    public static class Allocation {
        private Long openItemId;
        private String number;
        private BigDecimal outstanding;

        @DecimalMin(value = "0.00", message = "Amounts cannot be negative.")
        @Digits(integer = 16, fraction = 2, message = "Up to 2 decimal places.")
        private BigDecimal amount;
    }
}
