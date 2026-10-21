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

    /*
     * Stable per-device identifier generated ON the phone (UUID kept in
     * localStorage). The fraud-service needs it to detect the same token
     * being spent from two different devices (TOKEN_REUSE rule) — before
     * this existed the client always sent "" and that rule silently never
     * fired. It is NOT a security boundary by itself; the signed-receipt
     * layer (Step 0b) binds transactions to a real key.
     */
    private String deviceId;

    /*
     * Signed receipts (Step 0b). The wallet signs a canonical string of the
     * transaction with its ECDSA P-256 device key; we re-verify it here
     * before the transaction is even accepted. "publicKey" is the device's
     * public key as a JWK JSON string, "signature" a base64url raw r||s
     * signature over the canonical form. Forged or tampered transactions
     * fail verification and never enter the settlement pipeline.
     */
    private String signature;

    private String publicKey;

    /*Populated server-side by SyncController — the device never sends
     *its own userId (that would be spoofable). The userId comes from
     *the authenticated session or URL, and gets stamped onto every
     *transaction before Kafka publish so fraud-service can do
     *per-user velocity checks.
     */
    private String userId;
}
