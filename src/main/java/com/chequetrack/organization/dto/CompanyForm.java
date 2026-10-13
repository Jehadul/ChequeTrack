package com.chequetrack.organization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompanyForm {

    private Long version;

    @NotBlank(message = "Enter the company name.")
    @Size(max = 150)
    private String name;

    @Size(max = 300)
    private String address;

    @Size(max = 50)
    private String phone;

    @Email(message = "Enter a valid email address.")
    @Size(max = 150)
    private String email;
}
