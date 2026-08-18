package com.vendex.auth.grpc;

import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorMapperTest {

    @Test
    void preservesIntentionalGrpcAuthorizationStatuses() {
        var unauthenticated = Status.UNAUTHENTICATED
                .withDescription("missing or invalid bearer token")
                .asRuntimeException();
        var forbidden = Status.PERMISSION_DENIED
                .withDescription("cannot update another user's profile")
                .asRuntimeException();

        assertThat(ErrorMapper.map(unauthenticated).getStatus().getCode())
                .isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(ErrorMapper.map(forbidden).getStatus().getCode())
                .isEqualTo(Status.Code.PERMISSION_DENIED);
    }
}
