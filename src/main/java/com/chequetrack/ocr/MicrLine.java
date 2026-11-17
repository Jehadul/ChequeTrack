package com.chequetrack.ocr;

public record MicrLine(String chequeNumber, String routingNumber, String accountNumber, String transactionCode,
                       boolean routingKnown) {
}
