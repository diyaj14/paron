package com.offlinepay.ledger.exception;

public class LedgerException extends RuntimeException {
    private final String errorCode;

    public LedgerException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
