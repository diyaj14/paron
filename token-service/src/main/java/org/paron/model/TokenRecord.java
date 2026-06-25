package org.paron.model;


/*
 * JPA Entity — maps to the "tokens" table in Supabase.
 *
 * Every time a user requests an offline spending token,
 * one row is inserted here. This is the source of truth
 * for the state of every token ever issued.
 *
 * Table columns:
 *   id               — primary key (UUID, auto-generated)
 *   user_id          — who this token belongs to
 *   reservation_id   — the matching reservation in ledger-service
 *   token_value      — the full signed JWT string
 *   max_amount       — how much the user was allowed to spend offline
 *   spent_amount     — how much was actually spent (updated after settlement)
 *   status           — ACTIVE, USED, EXPIRED, INVALIDATED
 *   created_at       — when the token was issued
 *   expires_at       — when the token becomes invalid automatically
 *   settled_at       — when the sync-service finished settling this token
 */

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name="tokens", indexes= { @Index(name = "idx_token_user_id", columnList = "user_id"),
        @Index(name = "idx_token_status",  columnList = "status"),
        @Index(name = "idx_token_expires", columnList = "expires_at")})
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder //for flexibility in schema insertion
public class TokenRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // Who owns this token — matches the userId from the mobile app login
    @Column(name = "user_id", nullable = false)
    private String userId;

    // Foreign key reference to the reservation created in ledger-service
    // We store it as a String here because we call ledger-service via HTTP,
    // not via a shared database.
    @Column(name = "reservation_id", nullable = false)
    private String reservationId;

    // The complete signed JWT string — this is what gets sent to the device
    @Column(name = "token_value", nullable = false, columnDefinition = "TEXT")
    private String tokenValue;

    // The amount the user reserved for offline spending (e.g. 500.00)
    @Column(name = "max_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal maxAmount;

    // Starts at 0. Updated to the actual amount spent when sync-service
    // calls markAsUsed() after settlement. Useful for audit and analytics.
    @Column(name = "spent_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal spentAmount = BigDecimal.ZERO;

    // Current lifecycle state of this token (see TokenStatus enum)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TokenStatus status = TokenStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // Null until the sync-service settles and calls markAsUsed()
    @Column(name = "settled_at")
    private LocalDateTime settledAt;

}
