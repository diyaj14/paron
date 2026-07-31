package org.paron.fraudservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Data
@RequiredArgsConstructor
public class TransactionEvent {

    @NotBlank(message = "userId is required")
    String userId;

    @NotBlank(message = "deviceTransactionId is required")
    String deviceTransactionId;

    @NotBlank(message = "offlineToken is required")
    String offlineToken;

    String deviceId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be positive")
    BigDecimal amount;

    String merchantId;

    String transactedAt;

    String tokenExpiryTime;
}
