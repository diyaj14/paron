package org.paron.syncservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/*
made serialaizable because kafka `spring integration expects
message payload to be serialazable
 */
public class OfflineTransactionDto implements Serializable{
    @NotBlank(message="device transactionId is required")
    private String deviceTransactionId;

    @NotBlank(message="offline token is required")
    private String offlineToken;

    @NotNull(message="Amount is required")
    @DecimalMin(value="0.01",message="amount must be positive")
    private BigDecimal amount;

    private String merchantId;

    @NotNull(message="transaction time is required")
    private LocalDateTime transactedAt;
}
