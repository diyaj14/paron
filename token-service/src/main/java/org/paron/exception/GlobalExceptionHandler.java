package org.paron.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Catches all exceptions thrown anywhere in the service and converts them
 * into clean, consistent JSON error responses.
 *
 * Without this, Spring would return ugly HTML error pages or raw stack traces.
 * With this, the client always gets a structured JSON like:
 * {
 *   "errorCode":  "ACTIVE_TOKEN_EXISTS",
 *   "message":    "User user123 already has an active offline token...",
 *   "timestamp":  "2024-01-15T09:30:00"
 * }
 *
 * @RestControllerAdvice means this applies to ALL controllers in the service.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Handles our custom business exceptions (TokenException subclasses)
    @ExceptionHandler(TokenException.class)
    public ResponseEntity<Map<String, Object>> handleTokenException(TokenException ex) {
        log.error("Token business error: {} - {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildError(ex.getErrorCode(), ex.getMessage()));
    }

    // Handles @Valid annotation failures on request bodies
    // e.g. if amount is missing or expiryHours is out of range
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        // Collect all validation errors into a readable map
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName    = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        Map<String, Object> body = buildError("VALIDATION_FAILED", "Request validation failed");
        body.put("fieldErrors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // Catch-all for unexpected errors
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error in token-service", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    private Map<String, Object> buildError(String code, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("errorCode",  code);
        body.put("message",    message);
        body.put("timestamp",  LocalDateTime.now().toString());
        return body;
    }
}
