package com.offlinepay.ledger.exception;

/*
 * Thrown if someone tries to release or settle a reservation
 * that is no longer ACTIVE (already RELEASED or SETTLED).
 * This is the ledger-service's own idempotency guard — prevents
 * double-releasing or double-settling the same reservation.
 */
public class ReservationAlreadyClosedException extends LedgerException {
    public ReservationAlreadyClosedException(String reservationId, String currentStatus) {
        super("RESERVATION_ALREADY_CLOSED",
              "Reservation " + reservationId + " is already " + currentStatus +
              " and cannot be modified again.");
    }
}
