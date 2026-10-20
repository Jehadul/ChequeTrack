package com.chequetrack.received.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class DepositForm {

    @NotNull(message = "Choose the account to deposit into.")
    private Long accountId;

    @NotNull(message = "Enter the deposit date.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate depositDate;

    @NotEmpty(message = "Tick at least one cheque.")
    private List<Long> chequeIds = new ArrayList<>();

    @Size(max = 300)
    private String notes;
}
