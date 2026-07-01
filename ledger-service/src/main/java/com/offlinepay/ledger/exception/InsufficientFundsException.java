package com.offlinepay.ledger.exception;

import java.math.BigDecimal;

/*
 * Thrown when a user tries to reserve more than their available balance.
 *
 * This is the single most important validation in the whole offline
 * payment system — without this check, a user could reserve money
 * they don't actually have, breaking the entire trust model.
 */
public class InsufficientFundsException extends LedgerException {
    public InsufficientFundsException(String userId, BigDecimal requested, BigDecimal available) {
        super("INSUFFICIENT_FUNDS",
              "User " + userId + " requested ₹" + requested +
              " but only ₹" + available + " is available.");
    }
}
