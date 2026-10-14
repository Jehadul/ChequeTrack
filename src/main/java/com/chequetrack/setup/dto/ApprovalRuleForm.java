package com.chequetrack.setup.dto;

import com.chequetrack.setup.domain.ApprovalAction;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ApprovalRuleForm {

    private Long id;
    private Long version;

    @NotNull(message = "Choose the action.")
    private ApprovalAction action;

    @Min(value = 1, message = "Steps are numbered 1 to 9.")
    @Max(value = 9, message = "Steps are numbered 1 to 9.")
    private int stepOrder = 1;

    @NotNull(message = "Enter the amount from which this step applies (0 = always).")
    @DecimalMin(value = "0.00", message = "The amount cannot be negative.")
    @Digits(integer = 16, fraction = 2, message = "Up to 2 decimal places.")
    private BigDecimal minAmount = BigDecimal.ZERO;

    @NotNull(message = "Choose the approver role.")
    private Long approverRoleId;
}
