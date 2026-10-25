package com.chequetrack.issued.dto;

import com.chequetrack.issued.domain.Crossing;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** New cheque or edit of a draft. The account cannot change once the leaf is allocated. */
@Data
public class IssuedChequeForm {

    private Long id;
    private Long version;

    @NotNull(message = "Choose the bank account.")
    private Long accountId;

    @NotNull(message = "Choose the supplier.")
    private Long partyId;

    /** Name written on the cheque; the supplier's name when left empty. */
    @Size(max = 150)
    private String payeeName;

    @NotNull(message = "Enter the amount.")
    @DecimalMin(value = "0.01", message = "The amount must be more than zero.")
    @Digits(integer = 13, fraction = 2, message = "Up to 13 digits and 2 decimal places.")
    private BigDecimal amount;

    @NotNull(message = "Choose the crossing.")
    private Crossing crossing = Crossing.ACCOUNT_PAYEE;

    @NotNull(message = "Enter the cheque date.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate chequeDate;

    @Size(max = 300)
    private String narration;

    /** Set when this cheque replaces a void or stopped one. */
    private Long reissueOfId;

    /** Open bills of the supplier with how much of each this cheque pays (blank = not linked). */
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
