package com.vendex.notification.service;

public final class NotificationExceptions {
    private NotificationExceptions() {}

    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) { super(message); }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException() { super("notification resource not found"); }
    }

    public static class OwnershipException extends RuntimeException {
        public OwnershipException() { super("notification resource belongs to another vendor"); }
    }
}
