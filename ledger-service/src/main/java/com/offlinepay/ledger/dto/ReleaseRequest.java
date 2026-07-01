package com.offlinepay.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/*
 * What token-service sends to POST /api/v1/ledger/release
 * when a token expires unused.
 *
 * Matches LedgerServiceClient.releaseReservation():
 *   Map.of("reservationId", reservationId)
 */
@Data
public class ReleaseRequest {

    @NotBlank(message = "reservationId is required")
    private String reservationId;
}
