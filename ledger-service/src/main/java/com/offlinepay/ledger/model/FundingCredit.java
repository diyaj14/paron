package com.offlinepay.ledger.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable idempotency record for a verified external funding event.
 * A fundingId may credit a demo wallet once, even if a caller retries.
 */
@Entity
@Table(name = "wallet_funding_events", uniqueConstraints =
        @UniqueConstraint(name = "uk_wallet_funding_event_funding_id", columnNames = "funding_id"))
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FundingCredit {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "funding_id", nullable = false, updatable = false)
    private UUID fundingId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private String userId;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
