package com.offlinepay.ledger.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/*
 * What token-service sends to POST /api/v1/ledger/reserve
 *
 * This matches exactly what LedgerServiceClient.reserveFunds() builds:
 *   Map.of("userId", userId, "amount", amount)
 */
@Data
public class ReserveRequest {

    @NotBlank(message = "userId is required")
    private String userId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be positive")
    private BigDecimal amount;
}
