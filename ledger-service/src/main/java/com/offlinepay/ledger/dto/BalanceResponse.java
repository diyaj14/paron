package com.offlinepay.ledger.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/*
 * Returned by GET /api/v1/ledger/balance/{userId}
 * Lets the mobile app show "you have ₹X available to reserve offline".
 */
@Data
@Builder
public class BalanceResponse {
    private String userId;
    private BigDecimal totalBalance;
    private BigDecimal availableBalance;
    private BigDecimal reservedAmount;   // totalBalance - availableBalance, shown for clarity
}
