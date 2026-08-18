package com.vendex.buylist.grpc;

import com.vendex.buylist.service.BuyListExceptions;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ErrorMapper {
    private static final Logger log = LoggerFactory.getLogger(ErrorMapper.class);

    private ErrorMapper() {}

    public static StatusRuntimeException map(Throwable error) {
        if (error instanceof BuyListExceptions.WantedCardNotFoundException
                || error instanceof BuyListExceptions.CardNotFoundException) {
            return Status.NOT_FOUND.withDescription(error.getMessage()).asRuntimeException();
        }
        if (error instanceof BuyListExceptions.OwnershipException) {
            return Status.PERMISSION_DENIED.withDescription(error.getMessage()).asRuntimeException();
        }
        if (error instanceof BuyListExceptions.AlreadyWantedException) {
            return Status.ALREADY_EXISTS.withDescription(error.getMessage()).asRuntimeException();
        }
        if (error instanceof BuyListExceptions.ValidationException
                || error instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription(error.getMessage()).asRuntimeException();
        }
        if (error instanceof BuyListExceptions.DependencyUnavailableException) {
            return Status.UNAVAILABLE.withDescription(error.getMessage()).asRuntimeException();
        }
        log.error("unhandled exception in BuyListService", error);
        return Status.INTERNAL.withDescription("internal error").asRuntimeException();
    }
}
