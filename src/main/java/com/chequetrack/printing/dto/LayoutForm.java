package com.chequetrack.printing.dto;

import com.chequetrack.common.domain.AmountInWords;
import com.chequetrack.printing.domain.DateStyle;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LayoutForm {

    private Long id;
    private Long version;

    @NotNull(message = "Choose the bank account.")
    private Long accountId;

    @NotBlank(message = "Enter a name.")
    @Size(max = 100)
    private String name;

    @NotNull(message = "Enter the width of the leaf.")
    @DecimalMin(value = "50", message = "At least 50 mm.")
    @DecimalMax(value = "300", message = "At most 300 mm.")
    private Double widthMm;

    @NotNull(message = "Enter the height of the leaf.")
    @DecimalMin(value = "30", message = "At least 30 mm.")
    @DecimalMax(value = "150", message = "At most 150 mm.")
    private Double heightMm;

    @NotNull
    private AmountInWords.Language wordsLanguage = AmountInWords.Language.ENGLISH;

    @NotNull
    private DateStyle dateFormat = DateStyle.PLAIN;

    private boolean protectionStars = true;

    @Size(max = 150)
    private String signatoryCompany;

    @Size(max = 200)
    private String signatoryNames;
}
