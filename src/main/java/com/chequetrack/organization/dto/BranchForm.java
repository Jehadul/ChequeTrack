package com.chequetrack.organization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BranchForm {

    private Long id;
    private Long version;

    @NotBlank(message = "Enter a branch code.")
    @Pattern(regexp = "[A-Za-z0-9_-]{1,20}", message = "Use up to 20 letters, digits, - or _.")
    private String code;

    @NotBlank(message = "Enter the branch name.")
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
