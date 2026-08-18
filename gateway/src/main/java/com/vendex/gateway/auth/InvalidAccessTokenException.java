package com.vendex.gateway.auth;

public class InvalidAccessTokenException extends RuntimeException {

    public InvalidAccessTokenException() {
        super("The access token is invalid or expired");
    }
}
