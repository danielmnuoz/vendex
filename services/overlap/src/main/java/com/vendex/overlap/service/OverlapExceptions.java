package com.vendex.overlap.service;

public final class OverlapExceptions {
    private OverlapExceptions() {}

    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) { super(message); }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException() { super("overlap not found"); }
    }

    public static class OwnershipException extends RuntimeException {
        public OwnershipException() { super("vendor is not a participant in this overlap"); }
    }

    public static class InactiveException extends RuntimeException {
        public InactiveException() { super("inactive overlap cannot be newly saved"); }
    }
}
