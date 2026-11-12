package org.paron.paypalfunding.service;

public class LedgerCreditPendingException extends RuntimeException {
    private final Integer ledgerHttpStatus;

    private LedgerCreditPendingException(String message, Integer ledgerHttpStatus, Throwable cause) {
        super(message, cause);
        this.ledgerHttpStatus = ledgerHttpStatus;
    }

    public Integer getLedgerHttpStatus() {
        return ledgerHttpStatus;
    }

    static LedgerCreditPendingException rejected(int status, Throwable cause) {
        return new LedgerCreditPendingException(
                "The ledger returned HTTP " + status + " while crediting the captured PayPal order.",
                status,
                cause);
    }

    static LedgerCreditPendingException unreachable(Throwable cause) {
        return new LedgerCreditPendingException(
                "The ledger could not be reached while crediting the captured PayPal order.",
                null,
                cause);
    }
}
