package com.vendex.overlap.grpc;

import com.vendex.overlap.service.OverlapExceptions;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

final class ErrorMapper {
    private ErrorMapper() {}

    static StatusRuntimeException map(Exception exception) {
        if (exception instanceof OverlapExceptions.ValidationException
                || exception instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription(exception.getMessage())
                    .withCause(exception).asRuntimeException();
        }
        if (exception instanceof OverlapExceptions.NotFoundException) {
            return Status.NOT_FOUND.withDescription(exception.getMessage())
                    .asRuntimeException();
        }
        if (exception instanceof OverlapExceptions.OwnershipException) {
            return Status.PERMISSION_DENIED.withDescription(exception.getMessage())
                    .asRuntimeException();
        }
        if (exception instanceof OverlapExceptions.InactiveException) {
            return Status.FAILED_PRECONDITION.withDescription(exception.getMessage())
                    .asRuntimeException();
        }
        return Status.INTERNAL.withDescription("overlap operation failed")
                .withCause(exception).asRuntimeException();
    }
}
