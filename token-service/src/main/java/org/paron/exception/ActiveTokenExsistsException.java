package org.paron.exception;

/*
 * Thrown when a user tries to get a second offline token
 * while they already have an ACTIVE one.
 *
 * A user can only have one active offline session at a time —
 * this prevents splitting the reserved amount across multiple tokens.
 */
public class ActiveTokenExsistsException extends TokenException {
    public ActiveTokenExsistsException(String userId) {
        super("ACTIVE_TOKEN_EXISTS",
                "User " + userId + " already has an active offline token. " +
                        "Please wait for it to expire or be settled before requesting a new one.");
    }
}