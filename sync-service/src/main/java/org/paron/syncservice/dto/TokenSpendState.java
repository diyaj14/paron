package org.paron.syncservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/*
 * Mirror of token-service's /api/v1/tokens/state response — the dispute
 * arbiter's authoritative "how much of this token has already been spent?"
 * evidence. Never carries the raw JWT.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenSpendState {
    private UUID tokenId;
    private String userId;
    private String reservationId;
    private BigDecimal maxAmount;
    private BigDecimal spentAmount;
    private String status;
    private LocalDateTime expiresAt;
}