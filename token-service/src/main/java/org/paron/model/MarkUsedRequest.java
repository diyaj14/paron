package org.paron.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/*
 * Sent by sync-service AFTER settlement succeeds.
 * This tells token-service to flip the token status from ACTIVE → USED
 * and record how much was actually spent.
 */
@Data
public class MarkUsedRequest {

    @NotBlank(message = "token is required")
    private String token;

    @NotNull(message = "finalSpentAmount is required")
    private BigDecimal finalSpentAmount;
}

