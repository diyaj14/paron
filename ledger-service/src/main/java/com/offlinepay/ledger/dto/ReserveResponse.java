package com.offlinepay.ledger.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/*
 * What ledger-service sends back after a successful reservation.
 *
 * Token-service's LedgerServiceClient reads this back as:
 *   Map<String, String> response = restTemplate.postForObject(...)
 *   response.get("reservationId")
 *
 * So the JSON key MUST be exactly "reservationId" to match.
 */
@Data
@Builder
public class ReserveResponse {
    private String reservationId;
    private BigDecimal reservedAmount;
    private BigDecimal remainingAvailableBalance;
}
