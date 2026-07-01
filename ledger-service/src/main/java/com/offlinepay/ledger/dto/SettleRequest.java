package com.offlinepay.ledger.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/*
 * Sent by sync-service (built in the next stage) when settling an
 * offline session. Debits the actual spent amount and releases
 * whatever was left over in the reservation.
 *
 * Example: reserved ₹500, spent ₹300 offline
 *   -> debit ₹300 from totalBalance
 *   -> release the remaining ₹200 back to availableBalance
 */
@Data
public class SettleRequest {

    @NotBlank(message = "reservationId is required")
    private String reservationId;

    @NotNull(message = "spentAmount is required")
    @DecimalMin(value = "0.00", message = "spentAmount cannot be negative")
    private BigDecimal spentAmount;
}
