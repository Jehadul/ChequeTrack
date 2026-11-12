package com.chequetrack.report.model;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class ReportFilter {

    public enum Field { DATE, BRANCH, BANK, STATUS, PARTY, CHEQUE }

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;

    private Long branchId;
    private Long bankId;
    private String status;
    private Long partyId;

    /** Cheque history: which cheque, by kind and number. */
    private String chequeKind;
    private String chequeNumber;
}
