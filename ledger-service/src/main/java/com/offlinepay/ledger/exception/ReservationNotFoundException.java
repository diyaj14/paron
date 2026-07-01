package com.offlinepay.ledger.exception;

public class ReservationNotFoundException extends LedgerException {
    public ReservationNotFoundException(String reservationId) {
        super("RESERVATION_NOT_FOUND", "No reservation found with id: " + reservationId);
    }
}
