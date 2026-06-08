package com.vendex.cardcatalog.grpc;

import com.vendex.cardcatalog.service.CardExceptions;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Translates {@link com.vendex.cardcatalog.service.CardService} exceptions
 * to gRPC status codes. Same contract as auth's ErrorMapper: known domain
 * exceptions get a status + short message; everything else is logged with
 * stack trace and surfaced as {@code INTERNAL}.
 */
public final class ErrorMapper {

    private static final Logger log = LoggerFactory.getLogger(ErrorMapper.class);

    private ErrorMapper() {}

    public static StatusRuntimeException map(Throwable t) {
        if (t instanceof CardExceptions.CardNotFoundException) {
            return Status.NOT_FOUND.withDescription("card not found").asRuntimeException();
        }
        if (t instanceof CardExceptions.ValidationException ve) {
            return Status.INVALID_ARGUMENT.withDescription(ve.getMessage()).asRuntimeException();
        }
        if (t instanceof IllegalArgumentException iae) {
            return Status.INVALID_ARGUMENT.withDescription(iae.getMessage()).asRuntimeException();
        }
        log.error("unhandled exception in CardCatalogService", t);
        return Status.INTERNAL.withDescription("internal error").asRuntimeException();
    }
}
