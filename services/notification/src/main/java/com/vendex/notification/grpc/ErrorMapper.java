package com.vendex.notification.grpc;

import com.vendex.notification.service.NotificationExceptions;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

final class ErrorMapper {
    private ErrorMapper() {}

    static StatusRuntimeException map(Exception exception) {
        if (exception instanceof NotificationExceptions.ValidationException
                || exception instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription(exception.getMessage())
                    .withCause(exception).asRuntimeException();
        }
        if (exception instanceof NotificationExceptions.NotFoundException) {
            return Status.NOT_FOUND.withDescription(exception.getMessage())
                    .asRuntimeException();
        }
        if (exception instanceof NotificationExceptions.OwnershipException) {
            return Status.PERMISSION_DENIED.withDescription(exception.getMessage())
                    .asRuntimeException();
        }
        return Status.INTERNAL.withDescription("notification operation failed")
                .withCause(exception).asRuntimeException();
    }
}
