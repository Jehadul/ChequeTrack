package com.chequetrack.common.exception;

/**
 * A business rule was broken (e.g. duplicate cheque, illegal status change).
 * Pages show the message to the user; the API answers HTTP 422.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
