package com.vendex.auth.service;

/**
 * Domain exceptions raised by AuthService. Translated to gRPC Status codes
 * by {@link com.vendex.auth.grpc.ErrorMapper}; never leak their messages
 * verbatim to callers because they may include details a caller shouldn't
 * see (e.g. "no user with that email" vs "wrong password").
 */
public final class AuthExceptions {
    private AuthExceptions() {}

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() { super("invalid credentials"); }
    }

    public static class EmailAlreadyRegisteredException extends RuntimeException {
        public EmailAlreadyRegisteredException(String email) { super("email already registered: " + email); }
    }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String msg) { super(msg); }
    }

    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String msg) { super(msg); }
    }

    public static class ValidationException extends RuntimeException {
        public ValidationException(String msg) { super(msg); }
    }
}
