package com.offlinepay.ledger.exception;

public class AccountNotFoundException extends LedgerException {
    public AccountNotFoundException(String userId) {
        super("ACCOUNT_NOT_FOUND", "No account found for userId: " + userId);
    }
}
