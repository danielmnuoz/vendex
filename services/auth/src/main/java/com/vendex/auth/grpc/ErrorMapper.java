package com.vendex.auth.grpc;

import com.vendex.auth.service.AuthExceptions;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Translates AuthService exceptions to gRPC Status codes. The contract:
 *
 * <ul>
 *   <li>Known domain exceptions get a status code and a short, generic
 *       client-facing message. The exception's own message never crosses the
 *       wire — it may include details (e.g. which email was found) that a
 *       caller shouldn't see.</li>
 *   <li>Unknown exceptions get {@code INTERNAL} with a generic message; the
 *       full stack trace is logged server-side so a human can investigate.</li>
 * </ul>
 */
public final class ErrorMapper {

    private static final Logger log = LoggerFactory.getLogger(ErrorMapper.class);

    private ErrorMapper() {}

    public static StatusRuntimeException map(Throwable t) {
        if (t instanceof AuthExceptions.InvalidCredentialsException) {
            return Status.UNAUTHENTICATED.withDescription("invalid credentials").asRuntimeException();
        }
        if (t instanceof AuthExceptions.InvalidTokenException) {
            return Status.UNAUTHENTICATED.withDescription("invalid token").asRuntimeException();
        }
        if (t instanceof AuthExceptions.EmailAlreadyRegisteredException) {
            return Status.ALREADY_EXISTS.withDescription("email is already registered").asRuntimeException();
        }
        if (t instanceof AuthExceptions.UserNotFoundException) {
            return Status.NOT_FOUND.withDescription("user not found").asRuntimeException();
        }
        if (t instanceof AuthExceptions.ValidationException ve) {
            return Status.INVALID_ARGUMENT.withDescription(ve.getMessage()).asRuntimeException();
        }
        if (t instanceof IllegalArgumentException iae) {
            return Status.INVALID_ARGUMENT.withDescription(iae.getMessage()).asRuntimeException();
        }
        log.error("unhandled exception in AuthService", t);
        return Status.INTERNAL.withDescription("internal error").asRuntimeException();
    }
}
