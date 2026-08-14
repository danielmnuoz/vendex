package com.vendex.inventory.service;

public final class InventoryExceptions {
    private InventoryExceptions() {}

    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) { super(message); }
        public ValidationException(String message, Throwable cause) { super(message, cause); }
    }

    public static class ItemNotFoundException extends RuntimeException {
        public ItemNotFoundException() { super("inventory item not found"); }
    }

    public static class OwnershipException extends RuntimeException {
        public OwnershipException() { super("inventory item belongs to another vendor"); }
    }

    public static class CardNotFoundException extends RuntimeException {
        public CardNotFoundException(String cardId) { super("card not found: " + cardId); }
    }

    public static class DependencyUnavailableException extends RuntimeException {
        public DependencyUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
