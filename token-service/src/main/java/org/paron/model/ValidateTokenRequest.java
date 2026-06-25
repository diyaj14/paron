package org.paron.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/*
 * Sent by the sync-service when it wants to verify a token before settlement.
 *
 * The sync-service sends:
 *  - the raw JWT string
 *  - the total amount that was actually spent offline
 * We verify:
 *  1. JWT signature is valid (not tampered)
 *  2. JWT is not expired
 *  3. Token status in DB is still ACTIVE
 *  4. spentAmount does not exceed the token's maxAmount
 */
@Data
public class ValidateTokenRequest {

    @NotBlank(message = "token is required")
    private String token;

    @NotNull(message = "spentAmount is required")
    private BigDecimal spentAmount;
}