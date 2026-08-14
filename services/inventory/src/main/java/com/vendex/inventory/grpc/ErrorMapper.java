package com.vendex.inventory.grpc;

import com.vendex.inventory.service.InventoryExceptions;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ErrorMapper {
    private static final Logger log = LoggerFactory.getLogger(ErrorMapper.class);

    private ErrorMapper() {}

    public static StatusRuntimeException map(Throwable error) {
        if (error instanceof InventoryExceptions.ItemNotFoundException
                || error instanceof InventoryExceptions.CardNotFoundException) {
            return Status.NOT_FOUND.withDescription(error.getMessage()).asRuntimeException();
        }
        if (error instanceof InventoryExceptions.OwnershipException) {
            return Status.PERMISSION_DENIED.withDescription(error.getMessage()).asRuntimeException();
        }
        if (error instanceof InventoryExceptions.ValidationException
                || error instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription(error.getMessage()).asRuntimeException();
        }
        if (error instanceof InventoryExceptions.DependencyUnavailableException) {
            return Status.UNAVAILABLE.withDescription(error.getMessage()).asRuntimeException();
        }
        log.error("unhandled exception in InventoryService", error);
        return Status.INTERNAL.withDescription("internal error").asRuntimeException();
    }
}
