package com.chequetrack.received.dto;

import com.chequetrack.received.domain.ChequeType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A series of cheques from one customer, e.g. 12 monthly post-dated cheques. */
@Data
public class BulkChequeForm {

    private Long branchId;

    @NotNull(message = "Choose the customer.")
    private Long partyId;

    @NotNull
    private ChequeType chequeType = ChequeType.POST_DATED;

    @NotBlank(message = "Enter the first cheque number.")
    @Pattern(regexp = "[0-9]{1,20}", message = "The first number must be digits only, so it can count up.")
    private String firstNumber;

    @NotNull(message = "Enter how many cheques.")
    @Min(value = 2, message = "At least 2 cheques; use the normal form for one.")
    @Max(value = 60, message = "At most 60 cheques at once.")
    private Integer count = 12;

    @NotNull(message = "Enter the date of the first cheque.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate firstDate;

    @NotNull(message = "Enter the months between cheques.")
    @Min(value = 1, message = "At least 1 month.")
    @Max(value = 12, message = "At most 12 months.")
    private Integer everyMonths = 1;

    @NotNull(message = "Enter the amount of each cheque.")
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

    @NotNull(message = "Enter the date the cheques were received.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate receivedDate;

    @Size(max = 300)
    private String narration;
}
