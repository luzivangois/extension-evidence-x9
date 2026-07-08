package com.conviso.x9.ai;

/** Raised for any failure talking to an AI provider (network, HTTP status, or malformed response). */
public final class AiServiceException extends Exception {

    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
