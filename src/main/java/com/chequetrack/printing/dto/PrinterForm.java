package com.chequetrack.printing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PrinterForm {

    private Long id;
    private Long version;

    @NotBlank(message = "Enter the printer's name.")
    @Size(max = 80)
    private String name;

    @NotNull(message = "Enter 0 if no shift is needed.")
    @DecimalMin(value = "-20", message = "At most 20 mm.")
    @DecimalMax(value = "20", message = "At most 20 mm.")
    private Double offsetXMm = 0.0;

    @NotNull(message = "Enter 0 if no shift is needed.")
    @DecimalMin(value = "-20", message = "At most 20 mm.")
    @DecimalMax(value = "20", message = "At most 20 mm.")
    private Double offsetYMm = 0.0;

    @Size(max = 200)
    private String notes;
}
