package org.paron.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/*
 * Returned to sync-service after validating a token.
 *
 * Example — valid token:
 * {
 *   "valid":          true,
 *   "userId":         "user_abc123",
 *   "reservationId":  "res_xyz789",
 *   "maxAmount":      500.00,
 *   "spentAmount":    300.00,
 *   "reason":         null
 * }
 *
 * Example — invalid token:
 * {
 *   "valid":          false,
 *   "userId":         null,
 *   "reservationId":  null,
 *   "maxAmount":      null,
 *   "spentAmount":    null,
 *   "reason":         "TOKEN_EXPIRED"
 * }
 */
@Data
@Builder
public class ValidateTokenResponse {
    private boolean valid;
    private String userId;
    private String reservationId;
    private BigDecimal maxAmount;
    private BigDecimal spentAmount;
    private String reason;   // null if valid; error code if invalid
}