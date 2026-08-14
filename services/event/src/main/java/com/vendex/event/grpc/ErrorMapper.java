package com.vendex.event.grpc;

import com.vendex.event.service.EventExceptions;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ErrorMapper {
    private static final Logger log = LoggerFactory.getLogger(ErrorMapper.class);

    private ErrorMapper() {}

    public static StatusRuntimeException map(Throwable t) {
        if (t instanceof EventExceptions.EventNotFoundException) {
            return Status.NOT_FOUND.withDescription("event not found").asRuntimeException();
        }
        if (t instanceof EventExceptions.AlreadyRegisteredException) {
            return Status.ALREADY_EXISTS.withDescription(t.getMessage()).asRuntimeException();
        }
        if (t instanceof EventExceptions.OrganizerMismatchException) {
            return Status.PERMISSION_DENIED.withDescription(t.getMessage()).asRuntimeException();
        }
        if (t instanceof EventExceptions.ValidationException || t instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription(t.getMessage()).asRuntimeException();
        }
        log.error("unhandled exception in EventService", t);
        return Status.INTERNAL.withDescription("internal error").asRuntimeException();
    }
}
