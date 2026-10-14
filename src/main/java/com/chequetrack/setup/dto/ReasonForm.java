package com.chequetrack.setup.dto;

import com.chequetrack.setup.domain.ReasonType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReasonForm {

    private Long id;
    private Long version;

    @NotNull(message = "Choose the list.")
    private ReasonType reasonType;

    @NotBlank(message = "Enter a code.")
    @Pattern(regexp = "[A-Za-z0-9_-]{1,20}", message = "Use up to 20 letters, digits, - or _.")
    private String code;

    @NotBlank(message = "Enter the description.")
    @Size(max = 200)
    private String description;

    @Min(0) @Max(999)
    private int sortOrder = 10;
}
