package com.vendex.gateway.auth;

public class JwksUnavailableException extends RuntimeException {

    public JwksUnavailableException(Throwable cause) {
        super("Signing keys are temporarily unavailable", cause);
    }
}
