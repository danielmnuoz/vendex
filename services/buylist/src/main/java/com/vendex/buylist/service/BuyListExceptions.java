package com.vendex.buylist.service;

public final class BuyListExceptions {
    private BuyListExceptions() {}

    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) { super(message); }
    }

    public static class WantedCardNotFoundException extends RuntimeException {
        public WantedCardNotFoundException() { super("wanted card not found"); }
    }

    public static class OwnershipException extends RuntimeException {
        public OwnershipException() { super("wanted card belongs to another vendor"); }
    }

    public static class AlreadyWantedException extends RuntimeException {
        public AlreadyWantedException() { super("card is already on this vendor's buy list"); }
    }

    public static class CardNotFoundException extends RuntimeException {
        public CardNotFoundException(String cardId) { super("card not found: " + cardId); }
    }

    public static class DependencyUnavailableException extends RuntimeException {
        public DependencyUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
