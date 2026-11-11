package org.paron.paypalfunding.exception;

import org.paron.paypalfunding.service.PaypalCredentialsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class PaypalExceptionHandler {
    @ExceptionHandler(PaypalCredentialsException.class)
    public ResponseEntity<Map<String, String>> handlePaypalCredentials(PaypalCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", "PAYPAL_CREDENTIALS_INVALID",
                "message", exception.getMessage()));
    }
}
