package org.paron.model;


import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/*
 * What the mobile app sends when requesting an offline token.
 *
 * Example JSON body:
 * {
 *   "userId":       "user_abc123",
 *   "amount":       500.00,
 *   "expiryHours":  6
 * }
 *
 * @NotBlank   — field must not be null or empty string
 * @DecimalMin — amount must be at least ₹1
 * @DecimalMax — amount cannot exceed ₹10,000 (RBI offline limit guidance)
 * @Min / @Max — expiryHours between 1 and 24
 */

@Data
public class TokenRequest {

    @NotBlank(message = "userId is required")
    private String userId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "1.00", message = "Minimum offline amount is ₹1")
    @DecimalMax(value = "10000.00", message = "Maximum offline amount is ₹10,000")
    private BigDecimal amount;

    @Min(value = 1,  message = "Token must be valid for at least 1 hour")
    @Max(value = 24, message = "Token cannot be valid for more than 24 hours")
    private int expiryHours = 6;   // default to 6 hours if not specified
}

