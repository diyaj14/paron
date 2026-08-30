package com.offlinepay.ledger.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class MerchantBalanceResponse {
    private String merchantId;
    private String merchantName;
    private BigDecimal collectedBalance;
    private LocalDateTime createdAt;
}