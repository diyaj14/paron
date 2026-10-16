package com.offlinepay.ledger.exception;

import java.math.BigDecimal;

/*
 * Thrown when a user already has the per-user cap of active reservations
 * and tries to reserve more.
 *
 * This is the hard guard against a single user holding more than the
 * maximum allowed offline amount across all concurrently issued tokens.
 */
public class ReserveLimitExceededException extends LedgerException {
    public ReserveLimitExceededException(String userId, BigDecimal requested,
                                         BigDecimal alreadyReserved, BigDecimal limit) {
        super("RESERVE_LIMIT_EXCEEDED",
              "User " + userId + " already has ₹" + alreadyReserved + " reserved; " +
              "requesting ₹" + requested + " would exceed the ₹" + limit + " per-user cap.");
    }
}
