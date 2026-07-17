package org.paron.fraudservice.dto;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Data
@RequiredArgsConstructor
public class TransactionEvent {

    String userId;
    String deviceTransactionId;
    String offlineToken;
    BigDecimal amount;
    String merchantId;
    String transactedAt;
}
