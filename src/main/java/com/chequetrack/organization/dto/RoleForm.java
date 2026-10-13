package com.chequetrack.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class RoleForm {

    private Long id;
    private Long version;

    @NotBlank(message = "Enter a role code.")
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,29}", message = "Use 2-30 letters, digits or _, starting with a letter.")
    private String code;

    @NotBlank(message = "Enter the role name.")
    @Size(max = 100)
    private String name;

    @Size(max = 300)
    private String description;

    private Set<String> permissions = new HashSet<>();
}
