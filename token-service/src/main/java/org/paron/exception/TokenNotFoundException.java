package org.paron.exception;

public class TokenNotFoundException extends TokenException {
    public TokenNotFoundException(String detail) {
        super("TOKEN_NOT_FOUND", "Token not found: " + detail);
    }
}
