package com.offlinepay.ledger.model;

/*
 * Lifecycle states for a fund reservation.
 *
 * ACTIVE   — funds are currently locked, token-service has issued a JWT for this
 * RELEASED — funds were unlocked, either because:
 *              (a) sync-service settled the transactions, or
 *              (b) the token expired unused and cleanup released it
 * SETTLED  — a special case of RELEASED where actual spending happened
 *            (kept separate from RELEASED so reports can distinguish
 *             "money was spent" from "user never used it")
 */
public enum ReservationStatus {
    ACTIVE,
    RELEASED,
    SETTLED
}
