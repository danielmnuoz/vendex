package com.vendex.gateway.workflow;

import com.vendex.gateway.auth.GatewayAccessToken;
import com.vendex.gateway.config.GatewayProperties;
import io.grpc.Metadata;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;
import jakarta.servlet.http.HttpServletRequest;

import java.util.concurrent.TimeUnit;

public final class GrpcRequestSupport {

    private GrpcRequestSupport() {}

    public static <T extends AbstractStub<T>> T deadline(T stub, GatewayProperties properties) {
        return stub.withDeadlineAfter(properties.grpcDeadline().toMillis(), TimeUnit.MILLISECONDS);
    }

    public static <T extends AbstractStub<T>> T authenticated(
            T stub,
            GatewayProperties properties,
            HttpServletRequest request) {
        Metadata headers = new Metadata();
        headers.put(
                Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                "Bearer " + GatewayAccessToken.require(request));
        return deadline(stub, properties)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }
}
