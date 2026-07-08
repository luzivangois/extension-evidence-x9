package com.conviso.x9.api;

/** Raised for any failure talking to the Conviso Platform GraphQL API. */
public final class ConvisoApiException extends Exception {

    public ConvisoApiException(String message) {
        super(message);
    }

    public ConvisoApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
