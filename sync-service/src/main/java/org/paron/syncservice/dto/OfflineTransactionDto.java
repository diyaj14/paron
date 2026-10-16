package org.paron.syncservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/*
made serialaizable because kafka `spring integration expects
message payload to be serialazable
 */
@Data
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

    /*Populated server-side by SyncController — the device never sends
     *its own userId (that would be spoofable). The userId comes from
     *the authenticated session or URL, and gets stamped onto every
     *transaction before Kafka publish so fraud-service can do
     *per-user velocity checks.
     */
    private String userId;
}
