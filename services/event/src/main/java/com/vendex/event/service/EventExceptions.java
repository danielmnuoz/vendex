package com.vendex.event.service;

public final class EventExceptions {
    private EventExceptions() {}

    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) { super(message); }
    }

    public static class EventNotFoundException extends RuntimeException {
        public EventNotFoundException() { super("event not found"); }
    }

    public static class OrganizerMismatchException extends RuntimeException {
        public OrganizerMismatchException() { super("only the event organizer may update this event"); }
    }

    public static class AlreadyRegisteredException extends RuntimeException {
        public AlreadyRegisteredException() { super("user is already registered for this event"); }
    }
}
