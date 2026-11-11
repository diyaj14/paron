package com.offlinepay.ledger.exception;

public class ActiveReservationExistsException extends LedgerException {
    public ActiveReservationExistsException(String userId, int count) {
        super("ACTIVE_RESERVATION_EXISTS",
                "User " + userId + " already has " + count +
                        " active reservations. Resolve the existing reservation before creating another.");
    }
}
