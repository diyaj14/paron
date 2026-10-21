package org.paron.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/*
 * Read-only snapshot of a token's bookkeeping state, consumed by the
 * dispute arbiter ("AI judge") as authoritative evidence. The raw JWT is
 * deliberately NOT part of this response — it can not leave the service.
 */
@Data
@Builder
public class TokenSpendState {
    private UUID tokenId;
    private String userId;
    private String reservationId;
    private BigDecimal maxAmount;
    private BigDecimal spentAmount;
    private String status;
    private LocalDateTime expiresAt;
}