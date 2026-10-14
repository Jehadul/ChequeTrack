package com.chequetrack.setup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BankForm {

    private Long id;
    private Long version;

    @NotBlank(message = "Enter a short code, e.g. DBBL.")
    @Pattern(regexp = "[A-Za-z0-9_-]{2,20}", message = "Use 2-20 letters, digits, - or _.")
    private String code;

    @NotBlank(message = "Enter the bank name.")
    @Size(max = 150)
    private String name;

    @Pattern(regexp = "^$|[A-Za-z0-9]{8}([A-Za-z0-9]{3})?", message = "A SWIFT code has 8 or 11 letters/digits.")
    private String swiftCode;
}
