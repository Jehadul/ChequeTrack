package com.chequetrack.organization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class UserForm {

    private Long id;
    private Long version;

    /** Only used when creating; usernames cannot be changed. */
    @Pattern(regexp = "[A-Za-z0-9._-]{3,50}", message = "Use 3-50 letters, digits, '.', '_' or '-'.")
    private String username;

    @NotBlank(message = "Enter the full name.")
    @Size(max = 150)
    private String fullName;

    @Email(message = "Enter a valid email address.")
    @Size(max = 150)
    private String email;

    @NotNull(message = "Choose a role.")
    private Long roleId;

    @NotNull(message = "Choose the home branch.")
    private Long homeBranchId;

    private boolean allBranches;

    /** Extra branches besides the home branch. */
    private Set<Long> branchIds = new HashSet<>();
}
