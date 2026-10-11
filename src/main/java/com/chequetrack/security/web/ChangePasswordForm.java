package com.chequetrack.security.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangePasswordForm {

    @NotBlank(message = "Enter your current password.")
    private String currentPassword;

    @NotBlank(message = "Enter a new password.")
    private String newPassword;

    @NotBlank(message = "Repeat the new password.")
    private String confirmPassword;

    @AssertTrue(message = "The new passwords do not match.")
    public boolean isConfirmationMatching() {
        return newPassword == null || newPassword.equals(confirmPassword);
    }
}
