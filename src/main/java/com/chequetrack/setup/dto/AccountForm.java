package com.chequetrack.setup.dto;

import com.chequetrack.setup.domain.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AccountForm {

    private Long id;
    private Long version;

    @NotNull(message = "Choose the branch that operates this account.")
    private Long branchId;

    @NotNull(message = "Choose the bank branch.")
    private Long bankBranchId;

    @NotBlank(message = "Enter the account name.")
    @Size(max = 150)
    private String accountName;

    @NotBlank(message = "Enter the account number.")
    @Pattern(regexp = "[0-9A-Za-z-]{4,30}", message = "Use 4-30 digits, letters or -.")
    private String accountNumber;

    @NotNull(message = "Choose the account type.")
    private AccountType accountType = AccountType.CURRENT;

    @Size(max = 30)
    private String glCode;
}
