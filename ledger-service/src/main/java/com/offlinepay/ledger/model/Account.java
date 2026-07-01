package com.offlinepay.ledger.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/*
 * JPA Entity — maps to the "accounts" table in Supabase.
 *
 * This represents a single user's bank account inside our system.
 * In a real bank integration, this table would instead be a cached
 * mirror of the real bank's account data (synced periodically),
 * since we don't actually hold real money — the real bank does.
 * For this project, we treat this table as the source of truth.
 *
 * Two balance fields matter here:
 *   totalBalance      — the full amount the user actually has
 *   availableBalance  — totalBalance minus whatever is currently reserved
 *
 * Example:
 *   totalBalance = 2000, availableBalance = 1500
 *   means ₹500 is currently locked in an active reservation somewhere
 */
@Entity
@Table(name = "accounts", indexes = {
        @Index(name = "idx_account_user_id", columnList = "user_id", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // One account per user — enforced by the unique index above
    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(name = "total_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalBalance;

    @Column(name = "available_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal availableBalance;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
