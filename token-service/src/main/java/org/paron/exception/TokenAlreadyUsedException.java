package org.paron.exception;

/*
 * Thrown when mark-used is called on a token that is no longer ACTIVE
 * (already USED, EXPIRED, or INVALIDATED).
 *
 * mark-used is a one-time operation — the reservation is settled the
 * first time and the ledger refuses a second settlement. This guard
 * surfaces a clean business error (400) instead of a raw 500.
 */
public class TokenAlreadyUsedException extends TokenException {
    public TokenAlreadyUsedException(String tokenId, String status) {
        super("TOKEN_ALREADY_USED",
                "Token " + tokenId + " is already " + status +
                        " and cannot be marked as used again.");
    }
}
