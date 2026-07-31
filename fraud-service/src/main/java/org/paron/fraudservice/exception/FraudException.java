package org.paron.fraudservice.exception;

import lombok.Data;

@Data
public class FraudException extends RuntimeException {

    private final String errorCode;

    public FraudException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
