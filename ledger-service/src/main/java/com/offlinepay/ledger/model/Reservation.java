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
 * JPA Entity — maps to the "reservations" table in Supabase.
 *
 * One row here = one act of locking funds for a single offline session.
 * The "reservationId" that token-service stores and embeds in the JWT
 * is the String version of this entity's id (see ReservationService).
 *
 * Lifecycle:
 *   1. Created with status=ACTIVE when token-service calls /reserve
 *   2. Either:
 *      a) settled — settledAmount gets set, status -> SETTLED
 *      b) expired/unused — status -> RELEASED, full amount returned
 */
@Entity
@Table(name = "reservations", indexes = {
        @Index(name = "idx_reservation_user_id", columnList = "user_id"),
        @Index(name = "idx_reservation_status",  columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    // The amount that was originally locked
    @Column(name = "reserved_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal reservedAmount;

    // Filled in only after settlement — how much was actually spent
    @Column(name = "settled_amount", precision = 12, scale = 2)
    private BigDecimal settledAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "closed_at")
    private LocalDateTime closedAt;   // when it was released or settled
}
