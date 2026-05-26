package com.vendex.cardcatalog.service;

/**
 * Domain-level exceptions thrown by {@link CardService}. The gRPC layer
 * maps these to status codes — the service itself stays gRPC-agnostic so
 * a future REST surface (admin tooling, e.g.) can reuse it directly.
 */
public final class CardExceptions {

    private CardExceptions() {}

    public static class CardNotFoundException extends RuntimeException {
        public CardNotFoundException(String externalId) {
            super("card not found: " + externalId);
        }
    }

    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }
}
