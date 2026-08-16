package com.app.categorise.exception;

public final class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("The refresh token is invalid, expired, or revoked");
    }

    public InvalidRefreshTokenException(Throwable cause) {
        super("The refresh token is invalid, expired, or revoked", cause);
    }
}
