package com.chequetrack.chequebook.dto;

import com.chequetrack.chequebook.domain.ChequeBook;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class BookForm {

    @NotNull(message = "Choose the bank account.")
    private Long accountId;

    @Pattern(regexp = "^$|[A-Za-z]{1,10}", message = "Use up to 10 letters, or leave empty.")
    private String prefix;

    @NotNull(message = "Enter the first leaf number.")
    @Min(value = 1, message = "The first number must be at least 1.")
    private Long firstNumber;

    @NotNull(message = "Enter the number of leaves.")
    @Min(value = 1, message = "At least 1 leaf.")
    @Max(value = ChequeBook.MAX_LEAVES, message = "At most " + ChequeBook.MAX_LEAVES + " leaves per book.")
    private Integer leafCount = 25;

    @NotNull(message = "Enter how many digits the number has.")
    @Min(value = 1, message = "At least 1 digit.")
    @Max(value = 12, message = "At most 12 digits.")
    private Integer numberWidth = 7;

    @NotNull(message = "Enter the date the book was received.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate receivedDate;

    @Size(max = 300)
    private String notes;

    /** The last number must fit in the given number of digits. */
    @AssertTrue(message = "The last leaf number has more digits than the chosen width.")
    public boolean isWidthEnough() {
        if (firstNumber == null || leafCount == null || numberWidth == null) {
            return true;
        }
        long last = firstNumber + leafCount - 1;
        return String.valueOf(last).length() <= numberWidth;
    }
}
