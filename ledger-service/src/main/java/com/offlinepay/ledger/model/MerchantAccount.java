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
 * JPA Entity — maps to the "merchants" table in Supabase.
 *
 * One row here = one registered merchant. Every offline payment that a
 * customer syncs and settles also credits this merchant's collectedBalance
 * (called by sync-service after it settles the customer side).
 */
@Entity
@Table(name="merchants", indexes= {
        @Index(name = "idx_merchant_id", columnList = "merchant_id", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // The merchant's public ID — typed by the merchant app / customer PWA
    @Column(name = "merchant_id", nullable = false, unique = true)
    private String merchantId;

    @Column(name = "merchant_name")
    private String merchantName;

    // Total money this merchant has collected from settled offline payments
    @Column(name = "collected_balance", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal collectedBalance = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}