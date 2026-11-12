package org.paron.model;

import java.math.BigDecimal;

public record LedgerReserveResult(
        String reservationId,
        BigDecimal reservedAmount,
        BigDecimal settledAmount,
        BigDecimal remainingAvailableBalance,
        boolean reusedExisting) {
}
