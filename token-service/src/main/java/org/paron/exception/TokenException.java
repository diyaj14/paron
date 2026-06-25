package org.paron.exception;

/*
 * Base exception for all token-related business errors.
 *
 * By extending RuntimeException, Spring can catch these automatically
 * in the @ExceptionHandler without requiring try-catch everywhere.
 */
public class TokenException extends RuntimeException {

    private final String errorCode;

    public TokenException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
