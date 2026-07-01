package com.offlinepay.ledger.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SettleResponse {
    private String reservationId;
    private BigDecimal spentAmount;
    private BigDecimal releasedAmount;     // whatever was left over and unlocked
    private BigDecimal newTotalBalance;
    private BigDecimal newAvailableBalance;
}
