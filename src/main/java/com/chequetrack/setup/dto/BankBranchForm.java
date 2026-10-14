package com.chequetrack.setup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BankBranchForm {

    private Long id;
    private Long version;

    @NotBlank(message = "Enter the branch name.")
    @Size(max = 150)
    private String name;

    @NotBlank(message = "Enter the routing number.")
    @Pattern(regexp = "\\d{9}", message = "A routing number has exactly 9 digits.")
    private String routingNumber;

    @Size(max = 50)
    private String district;

    @Size(max = 300)
    private String address;
}
